package com.schoolbuddy.service;

import com.schoolbuddy.dto.ConversationMessageDTO;
import com.schoolbuddy.entity.Conversation;
import com.schoolbuddy.entity.Message;
import com.schoolbuddy.repository.ConversationRepository;
import com.schoolbuddy.repository.MessageRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public ConversationService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    public Conversation createConversation(
            Integer grade,
            String subject,
            String firstMessage
    ) {

        String title = createTitle(firstMessage);

        Conversation conversation =
                new Conversation(grade, subject, title);

        return conversationRepository.save(conversation);
    }

    public Optional<Conversation> findById(Long conversationId) {
        return conversationRepository.findById(conversationId);
    }

    public List<ConversationMessageDTO> getHistory(Long conversationId) {

        return messageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    private ConversationMessageDTO toDTO(Message message) {

        return new ConversationMessageDTO(
                message.getRole().name(),
                message.getContent()
        );
    }

    private String createTitle(String message) {

        if (message == null || message.isBlank()) {
            return "New Conversation";
        }

        String cleanedMessage = message.trim();

        if (cleanedMessage.length() <= 60) {
            return cleanedMessage;
        }

        return cleanedMessage.substring(0, 57) + "...";
    }
}