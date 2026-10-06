package com.schoolbuddy.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SarvamLLMClient implements LLMClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private final String apiKey;
    private final String apiUrl;
    private final String model;

    public SarvamLLMClient(
            ObjectMapper objectMapper,
            @Value("${sarvam.api.key:}") String apiKey,
            @Value("${sarvam.api.url:https://api.sarvam.ai/v1/chat/completions}") String apiUrl,
            @Value("${sarvam.api.model:sarvam-105b}") String model
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;

        this.restClient = RestClient
                .builder()
                .build();
    }


    /*
     * ============================================================
     * NORMAL TEXT REQUEST
     * ============================================================
     */

    @Override
    public String generate(
            String systemPrompt,
            String conversationContext,
            String userMessage
    ) {

        return callSarvam(
                systemPrompt,
                conversationContext,
                userMessage,
                null,
                null
        );
    }


    /*
     * ============================================================
     * MULTIMODAL REQUEST
     * ============================================================
     */

    @Override
    public String generate(
            String systemPrompt,
            String conversationContext,
            String userMessage,
            String imageBase64,
            String imageMimeType
    ) {

        return callSarvam(
                systemPrompt,
                conversationContext,
                userMessage,
                imageBase64,
                imageMimeType
        );
    }


    /*
     * ============================================================
     * SARVAM API CALL
     * ============================================================
     */

    private String callSarvam(
            String systemPrompt,
            String conversationContext,
            String userMessage,
            String imageBase64,
            String imageMimeType
    ) {

        if (apiKey == null || apiKey.isBlank()) {

            throw new IllegalStateException(
                    "Sarvam API key is not configured. "
                            + "Set the SARVAM_API_KEY environment variable."
            );
        }


        /*
         * --------------------------------------------------------
         * BUILD MESSAGES
         * --------------------------------------------------------
         */

        List<Map<String, Object>> messages =
                new ArrayList<>();


        /*
         * --------------------------------------------------------
         * SYSTEM MESSAGE
         * --------------------------------------------------------
         */

        Map<String, Object> systemMessage =
                new HashMap<>();

        systemMessage.put(
                "role",
                "system"
        );

        String fullSystemPrompt =
                systemPrompt
                        + "\n\n"
                        + conversationContext;

        systemMessage.put(
                "content",
                fullSystemPrompt
        );

        messages.add(systemMessage);


        /*
         * --------------------------------------------------------
         * USER MESSAGE
         * --------------------------------------------------------
         */

        Map<String, Object> userMessageObject =
                new HashMap<>();

        userMessageObject.put(
                "role",
                "user"
        );


        /*
         * --------------------------------------------------------
         * TEXT ONLY
         * --------------------------------------------------------
         */

        if (imageBase64 == null
                || imageBase64.isBlank()) {

            userMessageObject.put(
                    "content",
                    userMessage
            );

        } else {

            /*
             * ----------------------------------------------------
             * TEXT + IMAGE
             *
             * Sarvam multimodal format:
             *
             * data:image/png;base64,....
             * ----------------------------------------------------
             */

            List<Map<String, Object>> content =
                    new ArrayList<>();


            /*
             * TEXT PART
             */

            Map<String, Object> textPart =
                    new HashMap<>();

            textPart.put(
                    "type",
                    "text"
            );

            textPart.put(
                    "text",
                    userMessage
            );

            content.add(textPart);


            /*
             * IMAGE PART
             */

            Map<String, Object> imagePart =
                    new HashMap<>();

            imagePart.put(
                    "type",
                    "image_url"
            );

            Map<String, String> imageUrl =
                    new HashMap<>();

            String mimeType =
                    imageMimeType == null
                            || imageMimeType.isBlank()
                            ? "image/jpeg"
                            : imageMimeType;

            String dataUri =
                    "data:"
                            + mimeType
                            + ";base64,"
                            + imageBase64;

            imageUrl.put(
                    "url",
                    dataUri
            );

            imagePart.put(
                    "image_url",
                    imageUrl
            );

            content.add(imagePart);

            userMessageObject.put(
                    "content",
                    content
            );
        }

        messages.add(userMessageObject);


        /*
         * --------------------------------------------------------
         * REQUEST BODY
         * --------------------------------------------------------
         */

        Map<String, Object> requestBody =
                new HashMap<>();

        requestBody.put(
                "model",
                model
        );

        requestBody.put(
                "messages",
                messages
        );

        requestBody.put(
                "temperature",
                0.3
        );

        /*
         * Sarvam-105B can use reasoning tokens.
         *
         * We are using Sarvam as a fallback provider,
         * so disable reasoning and reserve the token budget
         * for the actual educational response.
         */

        requestBody.put(
                "reasoning_effort",
                null
        );

        requestBody.put(
                "max_tokens",
                1600
        );

        requestBody.put(
                "stream",
                false
        );


        /*
         * --------------------------------------------------------
         * SEND REQUEST
         * --------------------------------------------------------
         */

        String responseBody =
                restClient.post()
                        .uri(apiUrl)
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .header(
                                "api-subscription-key",
                                apiKey
                        )
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);


        /*
         * --------------------------------------------------------
         * PARSE RESPONSE
         * --------------------------------------------------------
         */

        try {

            JsonNode root =
                    objectMapper.readTree(
                            responseBody
                    );

            JsonNode choices =
                    root.path("choices");

            if (!choices.isArray()
                    || choices.isEmpty()) {

                throw new IllegalStateException(
                        "Sarvam returned no choices."
                );
            }

            JsonNode content =
                    choices
                            .get(0)
                            .path("message")
                            .path("content");

            if (content.isMissingNode()
                    || content.isNull()) {

                throw new IllegalStateException(
                        "Sarvam returned an empty response."
                );
            }

            String answer =
                    content.asText();

            if (answer == null
                    || answer.isBlank()) {

                throw new IllegalStateException(
                        "Sarvam returned an empty response."
                );
            }

            return answer;

        } catch (Exception exception) {

            throw new RuntimeException(
                    "Failed to parse Sarvam response: "
                            + exception.getMessage(),
                    exception
            );
        }
    }
}
