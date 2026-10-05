package com.schoolbuddy.service;

import com.schoolbuddy.dto.ChatRequestDTO;
import com.schoolbuddy.dto.ChatResponseDTO;

public interface ChatService {

    ChatResponseDTO chat(ChatRequestDTO request);

}