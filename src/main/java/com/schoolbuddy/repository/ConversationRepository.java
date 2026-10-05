package com.schoolbuddy.repository;

import com.schoolbuddy.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository
        extends JpaRepository<Conversation, Long> {
}