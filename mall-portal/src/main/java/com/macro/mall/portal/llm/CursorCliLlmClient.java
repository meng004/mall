package com.macro.mall.portal.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
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
        this(model,timeout,List.of("rtk","proxy","cursor-agent"));
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
            var command = new ArrayList<>(executable);
            command.addAll(List.of("--print","--mode","ask","--model",model,"--output-format","json",
                "--trust","--workspace",workspace.toString(),
                JSON.writeValueAsString(Map.of("system",systemInstruction,"user",userInput))));
            var builder = new ProcessBuilder(command).directory(workspace.toFile())
                .redirectOutput(response.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD);
            // Prefer the explicitly requested local login over an unrelated inherited API-key override.
            builder.environment().remove("CURSOR_API_KEY");
            process = builder.start();
            if (!process.waitFor(timeout.toMillis(),TimeUnit.MILLISECONDS))
                throw new IllegalStateException("Cursor model request timed out");
            if (process.exitValue() != 0 || Files.size(response) > 65536)
                throw new IllegalStateException("Cursor model request failed");
            var envelope = JSON.readTree(response.toFile());
            if (envelope == null || !envelope.path("is_error").isBoolean() || envelope.path("is_error").booleanValue()
                || !envelope.path("result").isTextual())
                throw new IllegalStateException("Cursor model returned an invalid response");
            return envelope.path("result").textValue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cursor model request interrupted");
        } catch (IOException ex) {
            throw new IllegalStateException("Cursor CLI unavailable or returned invalid output");
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
}
