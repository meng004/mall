package com.macro.mall.portal.llm;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import java.time.Duration;

/** OpenAI-compatible HTTP transport only. Credentials are never logged. */
public final class LangChain4jLlmClient implements LlmClient {
    private final ChatModel model;
    public LangChain4jLlmClient(String baseUrl, String modelName, String apiKey, String accessToken, Duration timeout) {
        boolean key = apiKey != null && !apiKey.isBlank();
        boolean token = accessToken != null && !accessToken.isBlank();
        if (key == token || baseUrl == null || baseUrl.isBlank() || modelName == null || modelName.isBlank())
            throw new IllegalArgumentException("Provide base URL, model and exactly one API credential");
        model = OpenAiChatModel.builder().baseUrl(baseUrl).modelName(modelName)
            .apiKey(key ? apiKey : accessToken).timeout(timeout).maxRetries(0)
            .logRequests(false).logResponses(false).build();
    }
    @Override public String generate(String systemInstruction, String userInput) {
        return model.chat(SystemMessage.from(systemInstruction),UserMessage.from(userInput)).aiMessage().text();
    }
}
