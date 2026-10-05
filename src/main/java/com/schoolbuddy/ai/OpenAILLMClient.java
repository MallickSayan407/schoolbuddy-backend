package com.schoolbuddy.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenAILLMClient implements LLMClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private final String apiKey;
    private final String apiUrl;
    private final String model;

    public OpenAILLMClient(
            ObjectMapper objectMapper,
            @Value("${openai.api.key:}") String apiKey,
            @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}") String apiUrl,
            @Value("${openai.api.model:gpt-5-mini}") String model
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public String generate(
            String systemPrompt,
            String conversationContext,
            String userMessage
    ) {
        return callOpenAI(
                systemPrompt,
                conversationContext,
                userMessage,
                null,
                null
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
        return callOpenAI(
                systemPrompt,
                conversationContext,
                userMessage,
                imageBase64,
                imageMimeType
        );
    }

    private String callOpenAI(
            String systemPrompt,
            String conversationContext,
            String userMessage,
            String imageBase64,
            String imageMimeType
    ) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OpenAI API key is not configured."
            );
        }

        String fullPrompt =
                systemPrompt
                        + "\n\n"
                        + conversationContext;

        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", fullPrompt);

        Map<String, Object> userMessageBody = new HashMap<>();
        userMessageBody.put("role", "user");

        if (imageBase64 == null || imageBase64.isBlank()) {

            userMessageBody.put(
                    "content",
                    userMessage
            );

        } else {

            String mimeType =
                    imageMimeType == null || imageMimeType.isBlank()
                            ? "image/jpeg"
                            : imageMimeType;

            String dataUrl =
                    "data:"
                            + mimeType
                            + ";base64,"
                            + imageBase64;

            List<Map<String, Object>> content =
                    new ArrayList<>();

            content.add(
                    Map.of(
                            "type", "text",
                            "text", userMessage
                    )
            );

            content.add(
                    Map.of(
                            "type", "image_url",
                            "image_url",
                            Map.of(
                                    "url", dataUrl
                            )
                    )
            );

            userMessageBody.put(
                    "content",
                    content
            );
        }

        List<Map<String, Object>> messages =
                List.of(
                        systemMessage,
                        userMessageBody
                );

        Map<String, Object> requestBody =
                new HashMap<>();

        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.3);
        requestBody.put("max_tokens", 1200);

        String responseBody =
                restClient.post()
                        .uri(apiUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(
                                "Authorization",
                                "Bearer " + apiKey
                        )
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);

        return extractText(responseBody);
    }

    private String extractText(String responseBody) {

        try {

            JsonNode root =
                    objectMapper.readTree(responseBody);

            JsonNode content =
                    root.path("choices")
                            .get(0)
                            .path("message")
                            .path("content");

            if (content.isMissingNode()
                    || content.isNull()
                    || content.asText().isBlank()) {

                throw new IllegalStateException(
                        "OpenAI returned an empty response."
                );
            }

            return content.asText();

        } catch (Exception exception) {

            throw new RuntimeException(
                    "Failed to parse OpenAI response.",
                    exception
            );
        }
    }
}