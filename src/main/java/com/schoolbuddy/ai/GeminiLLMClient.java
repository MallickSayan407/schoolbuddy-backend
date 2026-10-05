package com.schoolbuddy.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.schoolbuddy.exception.AiServiceException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(
        name = "ai.provider",
        havingValue = "gemini"
)
public class GeminiLLMClient implements LLMClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiUrl;
    private final String model;

    public GeminiLLMClient(
            ObjectMapper objectMapper,
            @Value("${gemini.api.key:}") String apiKey,
            @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta}") String apiUrl,
            @Value("${gemini.api.model:gemini-3.8-flash}") String model
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
        return callGemini(
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
        return callGemini(
                systemPrompt,
                conversationContext,
                userMessage,
                imageBase64,
                imageMimeType
        );
    }

    private String callGemini(
            String systemPrompt,
            String conversationContext,
            String userMessage,
            String imageBase64,
            String imageMimeType
    ) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Gemini API key is not configured. "
                            + "Set the GEMINI_API_KEY environment variable."
            );
        }

        /*
         * Combine SchoolBuddy's system instructions
         * with the previous conversation.
         */
        String fullPrompt =
                systemPrompt
                        + "\n\n"
                        + conversationContext;

        /*
         * Build contents[]
         */
        Map<String, Object> content = new HashMap<>();
        content.put("role", "user");

        List<Map<String, Object>> parts = new ArrayList<>();

        /*
         * Text part
         */
        Map<String, Object> textPart = new HashMap<>();

        textPart.put(
                "text",
                fullPrompt
                        + "\n\nStudent question:\n"
                        + userMessage
        );

        parts.add(textPart);

        /*
         * Optional image part
         */
        if (imageBase64 != null && !imageBase64.isBlank()) {

            String mimeType =
                    imageMimeType == null || imageMimeType.isBlank()
                            ? "image/jpeg"
                            : imageMimeType;

            Map<String, Object> imagePart = new HashMap<>();

            imagePart.put(
                    "inline_data",
                    Map.of(
                            "mime_type", mimeType,
                            "data", imageBase64
                    )
            );

            parts.add(imagePart);
        }

        content.put("parts", parts);

        List<Map<String, Object>> contents =
                List.of(content);

        /*
         * Request body
         */
        Map<String, Object> requestBody =
                new HashMap<>();

        requestBody.put(
                "contents",
                contents
        );

        /*
         * Generation configuration
         */
        Map<String, Object> generationConfig =
                new HashMap<>();

        generationConfig.put(
                "temperature",
                0.3
        );

        generationConfig.put(
                "maxOutputTokens",
                1200
        );

        requestBody.put(
                "generationConfig",
                generationConfig
        );

        /*
         * Gemini endpoint:
         *
         * /v1beta/models/{model}:generateContent
         */
        String endpoint =
                apiUrl
                        + "/models/"
                        + model
                        + ":generateContent";

        /*
         * Call Gemini with automatic retry
         * for temporary 503 / 429 errors.
         */
        String responseBody =
                callGeminiWithRetry(
                        endpoint,
                        requestBody
                );

        return extractText(responseBody);
    }

    /*
     * ============================================================
     * GEMINI RETRY HANDLING
     * ============================================================
     *
     * 503 = Gemini temporarily unavailable / high demand
     * 429 = rate limit exceeded
     *
     * We retry these errors because they can be temporary.
     */
    private String callGeminiWithRetry(
            String endpoint,
            Map<String, Object> requestBody
    ) {

        int maxAttempts = 3;

        for (int attempt = 1;
             attempt <= maxAttempts;
             attempt++) {

            try {

                return restClient.post()
                        .uri(endpoint)
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .header(
                                "x-goog-api-key",
                                apiKey
                        )
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);

            } catch (
                    org.springframework.web.client
                            .HttpServerErrorException.ServiceUnavailable
                            exception
            ) {

                /*
                 * Gemini returned HTTP 503.
                 */
                if (attempt == maxAttempts) {

                    throw new AiServiceException(
                            "SchoolBuddy AI is temporarily unavailable. "
                                    + "Please try again later.",
                            exception
                    );
                }

                waitBeforeRetry(attempt);

            } catch (
                    org.springframework.web.client
                            .HttpClientErrorException.TooManyRequests
                            exception
            ) {

                /*
                 * Gemini returned HTTP 429.
                 */
                if (attempt == maxAttempts) {

                    throw new AiServiceException(
                            "SchoolBuddy AI has temporarily reached its usage limit. "
                                    + "Please try again later.",
                            exception
                    );
                }

                waitBeforeRetry(attempt);
            }
        }

        throw new IllegalStateException(
                "Gemini request failed unexpectedly."
        );
    }

    /*
     * Exponential-style retry delay:
     *
     * Attempt 1 → 1.5 seconds
     * Attempt 2 → 3 seconds
     */
    private void waitBeforeRetry(int attempt) {

        try {

            long delayMillis =
                    switch (attempt) {
                        case 1 -> 1500;
                        case 2 -> 3000;
                        default -> 5000;
                    };

            Thread.sleep(delayMillis);

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();

            throw new RuntimeException(
                    "Gemini retry interrupted.",
                    exception
            );
        }
    }

    /*
     * ============================================================
     * RESPONSE PARSING
     * ============================================================
     */
    private String extractText(String responseBody) {

        try {

            JsonNode root =
                    objectMapper.readTree(
                            responseBody
                    );

            JsonNode candidates =
                    root.path("candidates");

            if (!candidates.isArray()
                    || candidates.isEmpty()) {

                throw new IllegalStateException(
                        "Gemini returned no candidates."
                );
            }

            JsonNode parts =
                    candidates
                            .get(0)
                            .path("content")
                            .path("parts");

            if (!parts.isArray()
                    || parts.isEmpty()) {

                throw new IllegalStateException(
                        "Gemini returned no response parts."
                );
            }

            StringBuilder answer =
                    new StringBuilder();

            for (JsonNode part : parts) {

                JsonNode text =
                        part.path("text");

                if (!text.isMissingNode()
                        && !text.isNull()) {

                    answer.append(
                            text.asText()
                    );
                }
            }

            if (answer.isEmpty()) {

                throw new IllegalStateException(
                        "Gemini returned an empty response."
                );
            }

            return answer.toString();

        } catch (Exception exception) {

            throw new RuntimeException(
                    "Failed to parse Gemini response: "
                            + exception.getMessage(),
                    exception
            );
        }
    }
}
