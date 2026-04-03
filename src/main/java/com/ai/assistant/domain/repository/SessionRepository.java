package com.ai.assistant.domain.repository;

import com.ai.assistant.domain.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    Optional<Session> findBySessionId(String sessionId);

    Optional<Session> findBySessionIdAndIsDeletedFalse(String sessionId);

    List<Session> findByExpiresAtBeforeAndIsDeletedFalse(LocalDateTime expiresAt);

    List<Session> findBySystemIdAndUserId(String systemId, String userId);

    @Modifying
    @Query("UPDATE Session s SET s.isDeleted = true WHERE s.sessionId = :sessionId")
    void softDeleteBySessionId(String sessionId);

    @Modifying
    @Query("UPDATE Session s SET s.isDeleted = true WHERE s.expiresAt < :now")
    int deleteExpiredSessions(LocalDateTime now);
}
