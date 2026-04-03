package com.ai.assistant.domain.repository;

import com.ai.assistant.domain.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByConversationId(String conversationId);

    List<Conversation> findBySessionIdAndIsDeletedFalseOrderByUpdatedAtDesc(String sessionId);

    Optional<Conversation> findByConversationIdAndSessionId(String conversationId, String sessionId);

    @Modifying
    @Query("UPDATE Conversation c SET c.messageCount = c.messageCount + 1 WHERE c.conversationId = :conversationId")
    void incrementMessageCount(String conversationId);

    @Modifying
    @Query("UPDATE Conversation c SET c.title = :title WHERE c.conversationId = :conversationId")
    void updateTitle(String conversationId, String title);
}
