package com.schoolbuddy.dto;

public class ChatResponseDTO {

    private Long conversationId;
    private String answer;

    public ChatResponseDTO() {
    }

    public ChatResponseDTO(Long conversationId, String answer) {
        this.conversationId = conversationId;
        this.answer = answer;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }
}