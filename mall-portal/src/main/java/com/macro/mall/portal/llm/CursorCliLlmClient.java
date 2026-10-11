package com.macro.mall.portal.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Uses Cursor's existing CLI login; never extracts or converts its credentials. */
public final class CursorCliLlmClient implements LlmClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final String model;
    private final Duration timeout;
    private final List<String> executable;

    public CursorCliLlmClient(String model, Duration timeout) {
        this(model, timeout, commandFrom(System.getenv("MALL_LLM_CURSOR_CMD")));
    }

    /** Splits {@code MALL_LLM_CURSOR_CMD} on spaces. Unset or blank uses {@code cursor-agent}. */
    static List<String> commandFrom(String configured) {
        if (configured == null || configured.isBlank()) return List.of("cursor-agent");
        return List.of(configured.trim().split(" +"));
    }
    // Package-private process seam lets tests exercise the real timeout/protocol without an online model.
    CursorCliLlmClient(String model, Duration timeout, List<String> executable) {
        if (model == null || model.isBlank() || timeout.isNegative() || timeout.isZero())
            throw new IllegalArgumentException("Model and positive timeout required");
        this.model = model; this.timeout = timeout; this.executable = List.copyOf(executable);
    }
    @Override public String generate(String systemInstruction, String userInput) {
        Path workspace = null;
        Process process = null;
        try {
            workspace = Files.createTempDirectory("mall-cursor-query-");
            Path cursor = Files.createDirectory(workspace.resolve(".cursor"));
            JSON.writeValue(cursor.resolve("cli.json").toFile(),Map.of("permissions",Map.of(
                "allow",List.of(),
                "deny",List.of("Shell(*)","Read(**)","Read(/**)","Write(**)","Write(/**)","WebFetch(*)","Mcp(*:*)"))));
            Path response = workspace.resolve("response.json");
            Path errorLog = workspace.resolve("stderr.txt");
            var command = new ArrayList<>(executable);
            command.addAll(List.of("--print","--mode","ask","--model",model,"--output-format","json",
                "--trust","--workspace",workspace.toString(),
                JSON.writeValueAsString(Map.of("system",systemInstruction,"user",userInput))));
            var builder = new ProcessBuilder(command).directory(workspace.toFile())
                .redirectOutput(response.toFile()).redirectError(errorLog.toFile());
            // Prefer the explicitly requested local login over an unrelated inherited API-key override.
            builder.environment().remove("CURSOR_API_KEY");
            process = builder.start();
            long started = System.nanoTime();
            boolean finished = process.waitFor(timeout.toMillis(),TimeUnit.MILLISECONDS);
            long elapsed = (System.nanoTime() - started) / 1_000_000L;
            String diagnosis = "elapsedMillis=" + elapsed + " stderr=" + stderrSummary(errorLog);
            if (!finished)
                throw new IllegalStateException("Cursor model request timed out " + diagnosis);
            diagnosis = "exit=" + process.exitValue() + " " + diagnosis;
            if (process.exitValue() != 0 || Files.size(response) > 65536)
                throw new IllegalStateException("Cursor model request failed " + diagnosis);
            com.fasterxml.jackson.databind.JsonNode envelope;
            try {
                envelope = JSON.readTree(response.toFile());
            } catch (IOException ex) {
                throw new IllegalStateException("Cursor model returned an invalid response " + diagnosis
                    + " " + ex.getClass().getSimpleName());
            }
            if (envelope == null || !envelope.path("is_error").isBoolean() || envelope.path("is_error").booleanValue()
                || !envelope.path("result").isTextual())
                throw new IllegalStateException("Cursor model returned an invalid response " + diagnosis);
            return envelope.path("result").textValue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cursor model request interrupted");
        } catch (IOException ex) {
            throw new IllegalStateException("Cursor CLI unavailable " + ex.getClass().getSimpleName() + ": "
                + limited(redactSecrets(String.valueOf(ex.getMessage()))));
        } finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                try { process.waitFor(2,TimeUnit.SECONDS); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
            if (workspace != null) {
                try (var files = Files.walk(workspace)) {
                    for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
                } catch (IOException ex) { throw new IllegalStateException("Cursor temporary workspace cleanup failed"); }
            }
        }
    }

    private static String stderrSummary(Path file) {
        try {
            if (file == null || !Files.isRegularFile(file)) return "";
            String text;
            try (var input = Files.newInputStream(file)) {
                text = new String(input.readNBytes(8192), StandardCharsets.UTF_8);
            }
            return limited(redactSecrets(text));
        } catch (IOException ex) {
            return ex.getClass().getSimpleName();
        }
    }

    private static String redactSecrets(String text) {
        if (text == null) return "";
        return text
            .replaceAll("(?i)bearer\\s+\\S+", "Bearer [redacted]")
            .replaceAll("(?i)(api[_-]?key|access[_-]?token|cursor[_-]?api[_-]?key|password|token|secret|credential)\"?\\s*[:=]\\s*(\"[^\"]*\"|'[^']*'|\\S+)", "$1=[redacted]")
            .replaceAll("sk-[A-Za-z0-9_\\-]{8,}", "[redacted]")
            .replaceAll("(?i)\\b(secret|credential|password|token)\\b", "[redacted]");
    }

    private static String limited(String text) {
        if (text == null) return "";
        text = text.trim();
        return text.length() > 240 ? text.substring(0, 240) + "...[truncated]" : text;
    }
}
