package com.schoolbuddy.controller;

import com.schoolbuddy.dto.ConversationMessageDTO;
import com.schoolbuddy.service.ConversationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@CrossOrigin(origins = "http://localhost:3000")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping("/{conversationId}")
    public List<ConversationMessageDTO> getConversation(
            @PathVariable Long conversationId
    ) {
        return conversationService.getHistory(conversationId);
    }
}