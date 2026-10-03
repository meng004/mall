package com.macro.mall.portal.llm;

/** Text generation boundary; product semantics remain in the query service. */
@FunctionalInterface
public interface LlmClient {
    String generate(String systemInstruction, String userInput);
}
