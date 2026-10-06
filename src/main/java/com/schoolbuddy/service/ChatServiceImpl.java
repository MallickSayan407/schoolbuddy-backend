package com.schoolbuddy.service;

import com.schoolbuddy.ai.LLMClient;
import com.schoolbuddy.ai.PromptService;
import com.schoolbuddy.dto.ChatRequestDTO;
import com.schoolbuddy.dto.ChatResponseDTO;
import com.schoolbuddy.dto.ConversationMessageDTO;
import com.schoolbuddy.entity.Conversation;
import com.schoolbuddy.entity.Message;
import com.schoolbuddy.entity.Role;
import com.schoolbuddy.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.List;

@Service
public class ChatServiceImpl implements ChatService {

    private final PromptService promptService;
    private final LLMClient llmClient;
    private final ConversationService conversationService;
    private final MessageRepository messageRepository;

    public ChatServiceImpl(
            PromptService promptService,
            LLMClient llmClient,
            ConversationService conversationService,
            MessageRepository messageRepository
    ) {
        this.promptService = promptService;
        this.llmClient = llmClient;
        this.conversationService = conversationService;
        this.messageRepository = messageRepository;
    }

    @Override
    @Transactional
    public ChatResponseDTO chat(ChatRequestDTO request) {

        /*
         * ---------------------------------------------------------
         * 1. Normalize the incoming user message
         * ---------------------------------------------------------
         *
         * The frontend may send:
         *
         * 1. Text only
         * 2. Image only
         * 3. Text + image
         *
         * Therefore message must not be assumed to be non-null.
         */

        String userText = request.getMessage();

        if (userText == null) {
            userText = "";
        }

        userText = userText.trim();

        /*
         * ---------------------------------------------------------
         * 2. Detect whether an image was supplied
         * ---------------------------------------------------------
         */

        boolean hasImage =
                request.getImageBase64() != null
                        && !request.getImageBase64().isBlank();

        /*
         * ---------------------------------------------------------
         * 3. Create a new conversation or retrieve an existing one
         * ---------------------------------------------------------
         */

        Conversation conversation;

        if (request.getConversationId() == null) {

            /*
             * If the user sends only an image, there may be no
             * textual message to use as the conversation title.
             *
             * Therefore provide a sensible fallback.
             */

            String conversationTitle;

            if (!userText.isBlank()) {
                conversationTitle = userText;
            } else if (hasImage) {
                conversationTitle = "Image Question";
            } else {
                conversationTitle = "New Conversation";
            }

            conversation = conversationService.createConversation(
                    request.getGrade(),
                    request.getSubject(),
                    conversationTitle
            );

        } else {

            conversation = conversationService.findById(
                    request.getConversationId()
            ).orElseThrow(
                    () -> new RuntimeException(
                            "Conversation not found: "
                                    + request.getConversationId()
                    )
            );
        }

        /*
         * ---------------------------------------------------------
         * 4. Retrieve previous conversation history
         * ---------------------------------------------------------
         *
         * We retrieve history BEFORE saving the current user
         * message so that the current question is not duplicated
         * inside the previous conversation context.
         */

        List<ConversationMessageDTO> history =
                conversationService.getHistory(
                        conversation.getId()
                );

        /*
         * ---------------------------------------------------------
         * 5. Prepare user content for database storage
         * ---------------------------------------------------------
         *
         * Base64 image data is NEVER stored in MySQL.
         *
         * We only store a small reference such as:
         *
         * Solve this equation.
         * [Image attached: math-question.jpg]
         *
         * For image-only messages:
         *
         * [Image attached: math-question.jpg]
         */

        StringBuilder storedUserContent = new StringBuilder();

        if (!userText.isBlank()) {
            storedUserContent.append(userText);
        }

        if (hasImage) {

            String imageName = request.getImageName();

            if (imageName == null || imageName.isBlank()) {
                imageName = "uploaded-image";
            }

            if (storedUserContent.length() > 0) {
                storedUserContent.append("\n");
            }

            storedUserContent.append(
                    "[Image attached: "
                            + imageName
                            + "]"
            );
        }

        /*
         * Safety fallback.
         *
         * Normally the frontend should never send an empty request,
         * but this prevents an empty database message from being
         * created if validation is relaxed later.
         */

        if (storedUserContent.length() == 0) {
            storedUserContent.append("[Empty user message]");
        }

        /*
         * ---------------------------------------------------------
         * 6. Save USER message
         * ---------------------------------------------------------
         */

        Message userMessage = new Message(
                Role.USER,
                storedUserContent.toString()
        );

        userMessage.setConversation(conversation);

        messageRepository.save(userMessage);

        /*
         * ---------------------------------------------------------
         * 7. Build the AI system prompt
         * ---------------------------------------------------------
         */

        String systemPrompt =
                promptService.buildSystemPrompt(request);

        /*
         * ---------------------------------------------------------
         * 8. Build previous conversation context
         * ---------------------------------------------------------
         */

        String conversationContext =
                promptService.buildConversationContext(history);

        /*
         * ---------------------------------------------------------
         * 9. Prepare message for LLM
         * ---------------------------------------------------------
         *
         * For image-only requests, the textual message is empty.
         *
         * The multimodal LLM receives the image separately through
         * imageBase64 + imageMimeType.
         *
         * We provide a useful textual instruction instead of passing
         * an empty string.
         */

        String llmUserMessage = userText;

        if (llmUserMessage.isBlank() && hasImage) {
            llmUserMessage =
                    "Please analyze the attached image and help me "
                            + "with the question shown in it.";
        }

        if (llmUserMessage.isBlank()) {
            llmUserMessage = "Please help me with this question.";
        }

        /*
         * ---------------------------------------------------------
         * 10. Generate AI response
         * ---------------------------------------------------------
         *
         * The 5-argument version supports multimodal requests.
         */

        String answer =
                llmClient.generate(
                        systemPrompt,
                        conversationContext,
                        llmUserMessage,
                        request.getImageBase64(),
                        request.getImageMimeType()
                );
        answer = normalizeMathFormatting(answer);

        /*
         * ---------------------------------------------------------
         * 11. Save ASSISTANT response
         * ---------------------------------------------------------
         */

        Message assistantMessage = new Message(
                Role.ASSISTANT,
                answer
        );

        assistantMessage.setConversation(conversation);

        messageRepository.save(assistantMessage);

        /*
         * ---------------------------------------------------------
         * 12. Return response to frontend
         * ---------------------------------------------------------
         */

        return new ChatResponseDTO(
                conversation.getId(),
                answer
        );
    }

    private String normalizeMathFormatting(String answer) {

        if (answer == null || answer.isBlank()) {
            return answer;
        }

        String normalized = answer;

        /*
         * ---------------------------------------------------------
         * 1. Convert code-wrapped fractions
         *
         * `3/8`
         *      ↓
         * $\frac{3}{8}$
         *
         * `24/6`
         *      ↓
         * $\frac{24}{6}$
         * ---------------------------------------------------------
         */

        Pattern fractionPattern =
                Pattern.compile(
                        "`\\s*(\\d+)\\s*/\\s*(\\d+)\\s*`"
                );

        Matcher fractionMatcher =
                fractionPattern.matcher(normalized);

        StringBuffer fractionResult =
                new StringBuffer();

        while (fractionMatcher.find()) {

            String numerator =
                    fractionMatcher.group(1);

            String denominator =
                    fractionMatcher.group(2);

            String replacement =
                    "$\\frac{"
                            + numerator
                            + "}{"
                            + denominator
                            + "}$";

            fractionMatcher.appendReplacement(
                    fractionResult,
                    Matcher.quoteReplacement(replacement)
            );
        }

        fractionMatcher.appendTail(fractionResult);

        normalized =
                fractionResult.toString();


        /*
         * ---------------------------------------------------------
         * 2. Convert code-wrapped division
         *
         * `24÷6`
         *      ↓
         * $24 \div 6$
         * ---------------------------------------------------------
         */

        Pattern divisionPattern =
                Pattern.compile(
                        "`\\s*(\\d+)\\s*÷\\s*(\\d+)\\s*`"
                );

        Matcher divisionMatcher =
                divisionPattern.matcher(normalized);

        StringBuffer divisionResult =
                new StringBuffer();

        while (divisionMatcher.find()) {

            String dividend =
                    divisionMatcher.group(1);

            String divisor =
                    divisionMatcher.group(2);

            String replacement =
                    "$"
                            + dividend
                            + " \\\\div "
                            + divisor
                            + "$";

            divisionMatcher.appendReplacement(
                    divisionResult,
                    Matcher.quoteReplacement(replacement)
            );
        }

        divisionMatcher.appendTail(divisionResult);

        normalized =
                divisionResult.toString();


        /*
         * ---------------------------------------------------------
         * 3. Convert code-wrapped multiplication
         *
         * `6×4=24`
         *      ↓
         * $6 \times 4 = 24$
         * ---------------------------------------------------------
         */

        Pattern multiplicationPattern =
                Pattern.compile(
                        "`\\s*(\\d+)\\s*×\\s*(\\d+)(?:\\s*=\\s*(\\d+))?\\s*`"
                );

        Matcher multiplicationMatcher =
                multiplicationPattern.matcher(normalized);

        StringBuffer multiplicationResult =
                new StringBuffer();

        while (multiplicationMatcher.find()) {

            String left =
                    multiplicationMatcher.group(1);

            String right =
                    multiplicationMatcher.group(2);

            String result =
                    multiplicationMatcher.group(3);

            String replacement;

            if (result != null) {

                replacement =
                        "$"
                                + left
                                + " \\\\times "
                                + right
                                + " = "
                                + result
                                + "$";

            } else {

                replacement =
                        "$"
                                + left
                                + " \\\\times "
                                + right
                                + "$";
            }

            multiplicationMatcher.appendReplacement(
                    multiplicationResult,
                    Matcher.quoteReplacement(replacement)
            );
        }

        multiplicationMatcher.appendTail(
                multiplicationResult
        );

        normalized =
                multiplicationResult.toString();


        /*
         * ---------------------------------------------------------
         * 4. Convert simple code-wrapped numeric values
         *
         * `24`
         *      ↓
         * $24$
         *
         * This only targets standalone numeric backticks.
         * Normal text/code backticks remain untouched.
         * ---------------------------------------------------------
         */

        Pattern numericPattern =
                Pattern.compile(
                        "`\\s*(\\d+(?:\\.\\d+)?)\\s*`"
                );

        Matcher numericMatcher =
                numericPattern.matcher(normalized);

        StringBuffer numericResult =
                new StringBuffer();

        while (numericMatcher.find()) {

            String number =
                    numericMatcher.group(1);

            String replacement =
                    "$"
                            + number
                            + "$";

            numericMatcher.appendReplacement(
                    numericResult,
                    Matcher.quoteReplacement(replacement)
            );
        }

        numericMatcher.appendTail(numericResult);

        normalized =
                numericResult.toString();


        /*
         * ---------------------------------------------------------
         * 5. Convert code-wrapped equations
         *
         * Example:
         *
         * `8-5=3`
         *      ↓
         * $8-5=3$
         *
         * This is intentionally limited to numeric arithmetic
         * so normal code examples are not modified.
         * ---------------------------------------------------------
         */

        Pattern equationPattern =
                Pattern.compile(
                        "`\\s*(\\d+\\s*[+\\-*=]\\s*\\d+(?:\\s*[+\\-*=]\\s*\\d+)*)\\s*`"
                );

        Matcher equationMatcher =
                equationPattern.matcher(normalized);

        StringBuffer equationResult =
                new StringBuffer();

        while (equationMatcher.find()) {

            String equation =
                    equationMatcher.group(1);

            String replacement =
                    "$"
                            + equation
                            + "$";

            equationMatcher.appendReplacement(
                    equationResult,
                    Matcher.quoteReplacement(replacement)
            );
        }

        equationMatcher.appendTail(
                equationResult
        );

        normalized =
                equationResult.toString();


        return normalized;
    }
}