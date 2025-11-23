package com.Balu.Movie_Recommend.Repositories;

import com.Balu.Movie_Recommend.Entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findTop20ByUserIdOrderByTimestampDesc(Long userId);
}