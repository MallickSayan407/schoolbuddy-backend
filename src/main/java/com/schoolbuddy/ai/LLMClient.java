package com.schoolbuddy.ai;

public interface LLMClient {

    String generate(
            String systemPrompt,
            String conversationContext,
            String userMessage
    );

    default String generate(
            String systemPrompt,
            String conversationContext,
            String userMessage,
            String imageBase64,
            String imageMimeType
    ) {
        return generate(
                systemPrompt,
                conversationContext,
                userMessage
        );
    }
}