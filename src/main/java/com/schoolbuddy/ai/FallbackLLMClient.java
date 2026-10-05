package com.schoolbuddy.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class FallbackLLMClient implements LLMClient {

    private static final Logger log =
            LoggerFactory.getLogger(
                    FallbackLLMClient.class
            );

    private final GeminiLLMClient gemini;
    private final SarvamLLMClient sarvam;
    private final OpenAILLMClient openai;
    private final OpenRouterLLMClient openrouter;
    private final MockLLMClient mock;

    public FallbackLLMClient(
            GeminiLLMClient gemini,
            SarvamLLMClient sarvam,
            OpenAILLMClient openai,
            OpenRouterLLMClient openrouter,
            MockLLMClient mock
    ) {
        this.gemini = gemini;
        this.sarvam = sarvam;
        this.openai = openai;
        this.openrouter = openrouter;
        this.mock = mock;
    }

    @Override
    public String generate(
            String systemPrompt,
            String conversationContext,
            String userMessage
    ) {

        return generateTextWithFallback(
                systemPrompt,
                conversationContext,
                userMessage
        );
    }

    @Override
    public String generate(
            String systemPrompt,
            String conversationContext,
            String userMessage,
            String imageBase64,
            String imageMimeType
    ) {

        boolean hasImage =
                imageBase64 != null
                        && !imageBase64.isBlank();

        if (!hasImage) {

            return generateTextWithFallback(
                    systemPrompt,
                    conversationContext,
                    userMessage
            );
        }

        /*
         * IMAGE / MULTIMODAL ROUTE
         *
         * Sarvam is deliberately excluded.
         */

        try {

            log.info(
                    "AI Router: trying Gemini for multimodal request"
            );

            return gemini.generate(
                    systemPrompt,
                    conversationContext,
                    userMessage,
                    imageBase64,
                    imageMimeType
            );

        } catch (Exception exception) {

            log.warn(
                    "Gemini multimodal failed: {}",
                    exception.getMessage()
            );
        }

        try {

            log.info(
                    "AI Router: trying OpenAI for multimodal request"
            );

            return openai.generate(
                    systemPrompt,
                    conversationContext,
                    userMessage,
                    imageBase64,
                    imageMimeType
            );

        } catch (Exception exception) {

            log.warn(
                    "OpenAI multimodal failed: {}",
                    exception.getMessage()
            );
        }

        try {

            log.info(
                    "AI Router: trying OpenRouter for multimodal request"
            );

            return openrouter.generate(
                    systemPrompt,
                    conversationContext,
                    userMessage,
                    imageBase64,
                    imageMimeType
            );

        } catch (Exception exception) {

            log.warn(
                    "OpenRouter multimodal failed: {}",
                    exception.getMessage()
            );
        }

        log.warn(
                "All real multimodal providers failed. Using MockLLMClient."
        );

        return mock.generate(
                systemPrompt,
                conversationContext,
                userMessage,
                imageBase64,
                imageMimeType
        );
    }

    private String generateTextWithFallback(
            String systemPrompt,
            String conversationContext,
            String userMessage
    ) {

        try {

            log.info(
                    "AI Router: trying Gemini for text request"
            );

            return gemini.generate(
                    systemPrompt,
                    conversationContext,
                    userMessage
            );

        } catch (Exception exception) {

            log.warn(
                    "Gemini text request failed: {}",
                    exception.getMessage()
            );
        }

        try {

            log.info(
                    "AI Router: trying Sarvam for text request"
            );

            return sarvam.generate(
                    systemPrompt,
                    conversationContext,
                    userMessage
            );

        } catch (Exception exception) {

            log.warn(
                    "Sarvam text request failed: {}",
                    exception.getMessage()
            );
        }

        try {

            log.info(
                    "AI Router: trying OpenAI for text request"
            );

            return openai.generate(
                    systemPrompt,
                    conversationContext,
                    userMessage
            );

        } catch (Exception exception) {

            log.warn(
                    "OpenAI text request failed: {}",
                    exception.getMessage()
            );
        }

        try {

            log.info(
                    "AI Router: trying OpenRouter for text request"
            );

            return openrouter.generate(
                    systemPrompt,
                    conversationContext,
                    userMessage
            );

        } catch (Exception exception) {

            log.warn(
                    "OpenRouter text request failed: {}",
                    exception.getMessage()
            );
        }

        log.warn(
                "All real text providers failed. Using MockLLMClient."
        );

        return mock.generate(
                systemPrompt,
                conversationContext,
                userMessage
        );
    }
}