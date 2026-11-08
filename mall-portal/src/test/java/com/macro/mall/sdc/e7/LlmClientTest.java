package com.macro.mall.sdc.e7;

import com.macro.mall.portal.llm.*;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class LlmClientTest {
    @Test void sdkSendsModelMessagesAndBearerCredentialsToConfiguredEndpoint() throws Exception {
        for (boolean token : new boolean[]{false,true}) {
            var body = new AtomicReference<String>(); var auth = new AtomicReference<String>();
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/v1/chat/completions", exchange -> {
                auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
                body.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                byte[] response = "{\"id\":\"test\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"test-model\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"reply\"},\"finish_reason\":\"stop\"}]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type","application/json");
                exchange.sendResponseHeaders(200,response.length); exchange.getResponseBody().write(response); exchange.close();
            });
            server.start();
            try {
                var client = new LangChain4jLlmClient("http://127.0.0.1:"+server.getAddress().getPort()+"/v1",
                    "test-model", token ? "" : "test-key", token ? "test-token" : "",Duration.ofSeconds(2));
                assertEquals("reply", client.generate("system instruction","user input"));
                assertEquals("Bearer "+(token ? "test-token" : "test-key"),auth.get());
                var json = new ObjectMapper().readTree(body.get());
                assertEquals("test-model",json.path("model").asText());
                assertEquals("system",json.path("messages").get(0).path("role").asText());
                assertEquals("user input",json.path("messages").get(1).path("content").asText());
                assertFalse(json.has("tools"));
            } finally {server.stop(0);}
        }
    }
    @Test void authenticationFailureAndTimeoutAreNotSuccess() throws Exception {
        for (boolean timeout : new boolean[]{false,true}) {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/v1/chat/completions", exchange -> {
                if (timeout) try { Thread.sleep(400); } catch (InterruptedException ex) {Thread.currentThread().interrupt();}
                byte[] body = "{\"error\":{\"message\":\"unauthorized\",\"type\":\"invalid_api_key\"}}".getBytes();
                try {exchange.sendResponseHeaders(401,body.length);exchange.getResponseBody().write(body);} finally {exchange.close();}
            });
            server.start();
            try {
                var client = new LangChain4jLlmClient("http://127.0.0.1:"+server.getAddress().getPort()+"/v1","test","bad","",Duration.ofMillis(100));
                assertThrows(RuntimeException.class,()->client.generate("s","u"));
            } finally {server.stop(0);}
        }
    }
    @Test void configurationIsOptionalButAmbiguousCredentialsAreRejected() {
        var config = new LlmConfiguration();
        assertThrows(IllegalStateException.class, () -> config.llmClient("openai-compatible","","","","").generate("s","u"));
        assertInstanceOf(CursorCliLlmClient.class,config.llmClient("cursor","","","",""));
        assertThrows(IllegalArgumentException.class, () -> config.llmClient("unknown","","","",""));
        assertThrows(IllegalArgumentException.class, () -> new LangChain4jLlmClient("https://example.com/v1","model","key","token",Duration.ofSeconds(1)));
    }
}
