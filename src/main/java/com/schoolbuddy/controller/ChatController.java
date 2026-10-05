package com.schoolbuddy.controller;

import com.schoolbuddy.dto.ChatRequestDTO;
import com.schoolbuddy.dto.ChatResponseDTO;
import com.schoolbuddy.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponseDTO chat(@Valid @RequestBody ChatRequestDTO request) {
        return chatService.chat(request);
    }
}