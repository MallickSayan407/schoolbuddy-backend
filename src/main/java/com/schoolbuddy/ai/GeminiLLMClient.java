package com.schoolbuddy.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbuddy.exception.AiServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiLLMClient implements LLMClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private final String apiKey;
    private final String apiUrl;
    private final List<String> models;

    public GeminiLLMClient(
            ObjectMapper objectMapper,
            @Value("${gemini.api.key:}") String apiKey,
            @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta}") String apiUrl,
            @Value("${gemini.models:gemini-3.8-flash}") String modelsConfig
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;

        this.models = Arrays.stream(modelsConfig.split(","))
                .map(String::trim)
                .filter(model -> !model.isBlank())
                .toList();

        this.restClient = RestClient.builder().build();
    }

    // ============================================================
    // TEXT GENERATION
    // ============================================================

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

    // ============================================================
    // MULTIMODAL GENERATION
    // Text + Image -> Text
    // ============================================================

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

    // ============================================================
    // GEMINI MODEL CASCADE
    // ============================================================

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

        if (models.isEmpty()) {
            throw new IllegalStateException(
                    "No Gemini models are configured."
            );
        }

        /*
         * Combine SchoolBuddy system instructions
         * with previous conversation context.
         */
        String fullPrompt =
                systemPrompt
                        + "\n\n"
                        + conversationContext;

        /*
         * Build Gemini contents[]
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
         * ========================================================
         * MODEL CASCADE
         * ========================================================
         *
         * Example:
         *
         * 1. gemini-3.8-flash
         * 2. gemini-3.7-flash
         * 3. gemini-3.6-flash
         * 4. gemini-3.5-flash
         * 5. gemini-3.5-flash-lite
         * 6. gemini-3.1-flash-lite
         *
         * If one model fails because of quota/rate limit/server
         * availability, the next model is attempted.
         */

        Exception lastException = null;

        for (String model : models) {

            try {

                System.out.println(
                        "AI Router: trying Gemini model: "
                                + model
                );

                String responseBody =
                        callSingleGeminiModel(
                                model,
                                requestBody
                        );

                String answer =
                        extractText(responseBody);

                System.out.println(
                        "AI Router: Gemini model succeeded: "
                                + model
                );

                return answer;

            } catch (Exception exception) {

                lastException = exception;

                System.err.println(
                        "AI Router: Gemini model failed: "
                                + model
                                + " -> "
                                + exception.getMessage()
                );

                /*
                 * Continue to the next Gemini model.
                 */
            }
        }

        /*
         * All configured Gemini models failed.
         */
        throw new AiServiceException(
                "All configured Gemini models are currently unavailable. "
                        + "Please try again later.",
                lastException
        );
    }

    // ============================================================
    // SINGLE GEMINI MODEL REQUEST
    // ============================================================

    private String callSingleGeminiModel(
            String model,
            Map<String, Object> requestBody
    ) {

        String endpoint =
                apiUrl
                        + "/models/"
                        + model
                        + ":generateContent";

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
                HttpClientErrorException.TooManyRequests exception
        ) {

            /*
             * HTTP 429
             *
             * Usually quota/rate-limit related.
             *
             * Do NOT perform long retries here.
             * Immediately move to the next Gemini model.
             */

            throw new RuntimeException(
                    "HTTP 429 - Gemini model quota/rate limit reached.",
                    exception
            );

        } catch (
                HttpClientErrorException.BadRequest exception
        ) {

            /*
             * HTTP 400
             *
             * Usually indicates an invalid request/model configuration.
             * Move to the next configured model.
             */

            throw new RuntimeException(
                    "HTTP 400 - Invalid Gemini request.",
                    exception
            );

        } catch (
                HttpClientErrorException.NotFound exception
        ) {

            /*
             * HTTP 404
             *
             * Model may not exist or may not be available.
             */

            throw new RuntimeException(
                    "HTTP 404 - Gemini model not found.",
                    exception
            );

        } catch (
                HttpClientErrorException.Forbidden exception
        ) {

            /*
             * HTTP 403
             *
             * API key / permission / access issue.
             */

            throw new RuntimeException(
                    "HTTP 403 - Gemini access forbidden.",
                    exception
            );

        } catch (
                HttpServerErrorException.InternalServerError exception
        ) {

            /*
             * HTTP 500
             */
            throw new RuntimeException(
                    "HTTP 500 - Gemini internal server error.",
                    exception
            );

        } catch (
                HttpServerErrorException.BadGateway exception
        ) {

            /*
             * HTTP 502
             */
            throw new RuntimeException(
                    "HTTP 502 - Gemini bad gateway.",
                    exception
            );

        } catch (
                HttpServerErrorException.ServiceUnavailable exception
        ) {

            /*
             * HTTP 503
             */
            throw new RuntimeException(
                    "HTTP 503 - Gemini service unavailable.",
                    exception
            );

        } catch (
                HttpServerErrorException.GatewayTimeout exception
        ) {

            /*
             * HTTP 504
             */
            throw new RuntimeException(
                    "HTTP 504 - Gemini gateway timeout.",
                    exception
            );
        }
    }

    // ============================================================
    // RESPONSE PARSING
    // ============================================================

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