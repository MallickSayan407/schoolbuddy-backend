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
}