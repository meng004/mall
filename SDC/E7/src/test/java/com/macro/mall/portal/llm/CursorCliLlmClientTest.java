package com.macro.mall.portal.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CursorCliLlmClientTest {
    private List<String> command(String mode, Path marker) {
        return List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),
            "-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),
            Stub.class.getName(),mode,marker.toString());
    }
    @Test void isolatesWorkspaceDeniesToolsAndEncodesInput() throws Exception {
        Path marker = Files.createTempFile("cursor-test-", ".txt");
        try {
            var client = new CursorCliLlmClient("chosen-model",Duration.ofSeconds(5),command("success",marker));
            String workspace = client.generate("system instruction","quote \" and newline\n input");
            assertFalse(Files.exists(Path.of(workspace)),"temporary workspace must be removed");
            assertEquals("validated",Files.readString(marker));
        } finally { Files.deleteIfExists(marker); }
    }
    @Test void failuresNeverExposeStderrOrReturnErrorEnvelope() throws Exception {
        Path marker = Files.createTempFile("cursor-test-", ".txt");
        try {
            for (String mode : List.of("exit","error","invalid")) {
                var client = new CursorCliLlmClient("chosen-model",Duration.ofSeconds(5),command(mode,marker));
                var ex = assertThrows(IllegalStateException.class,()->client.generate("system instruction","quote \" and newline\n input"));
                assertFalse(ex.toString().contains("secret"));
                assertFalse(Files.exists(Path.of(Files.readString(marker))));
            }
        } finally { Files.deleteIfExists(marker); }
    }
    @Test void timeoutTerminatesProcessAndRemovesWorkspace() throws Exception {
        Path marker = Files.createTempFile("cursor-test-", ".txt");
        try {
            var client = new CursorCliLlmClient("chosen-model",Duration.ofSeconds(2),command("timeout",marker));
            assertThrows(IllegalStateException.class,()->client.generate("s","u"));
            String[] data = Files.readString(marker).split("\n");
            assertFalse(ProcessHandle.of(Long.parseLong(data[0])).map(ProcessHandle::isAlive).orElse(false));
            assertFalse(Files.exists(Path.of(data[1])));
        } finally { Files.deleteIfExists(marker); }
    }
    public static class Stub {
        public static void main(String[] args) throws Exception {
            String mode = args[0]; Path marker = Path.of(args[1]);
            List<String> flags = Arrays.asList(args);
            Path workspace = Path.of(flags.get(flags.indexOf("--workspace")+1));
            if (mode.equals("timeout")) {
                Files.writeString(marker,ProcessHandle.current().pid()+"\n"+workspace);
                Thread.sleep(60000); return;
            }
            Files.writeString(marker,workspace.toString());
            if (mode.equals("exit")) { System.err.println("secret credential"); System.exit(2); }
            if (mode.equals("error")) { System.out.println("{\"is_error\":true,\"result\":\"secret\"}"); return; }
            if (mode.equals("invalid")) { System.out.println("not json secret"); return; }
            var json = new ObjectMapper();
            var config = json.readTree(Files.readString(workspace.resolve(".cursor/cli.json")));
            Set<String> denied = new HashSet<>();
            config.path("permissions").path("deny").forEach(n -> denied.add(n.asText()));
            if (!denied.containsAll(List.of("Shell(*)","Read(**)","Read(/**)","Write(**)","Write(/**)","WebFetch(*)","Mcp(*:*)")))
                throw new AssertionError("permissions missing");
            if (!config.path("permissions").path("allow").isEmpty()) throw new AssertionError("unexpected allow");
            if (!flags.contains("--print") || !flags.contains("--trust") || !flags.get(flags.indexOf("--mode")+1).equals("ask")
                || !flags.get(flags.indexOf("--model")+1).equals("chosen-model") || !flags.get(flags.indexOf("--output-format")+1).equals("json"))
                throw new AssertionError("bad CLI options");
            var prompt = json.readTree(args[args.length-1]);
            if (!prompt.path("system").asText().equals("system instruction") || !prompt.path("user").asText().equals("quote \" and newline\n input"))
                throw new AssertionError("prompt not safely encoded");
            if (!Path.of("").toRealPath().equals(workspace.toRealPath())) throw new AssertionError("wrong working directory");
            Files.writeString(marker,"validated");
            System.out.println(json.writeValueAsString(Map.of("is_error",false,"result",workspace.toString())));
        }
    }
}
