package com.ai.assistant.domain.repository;

import com.ai.assistant.domain.entity.GeneratedDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, Long> {

    Optional<GeneratedDocument> findByDocumentId(String documentId);

    List<GeneratedDocument> findByCreatedBy(String createdBy);

    List<GeneratedDocument> findBySessionId(String sessionId);

    /**
     * 删除过期文档
     */
    @Modifying
    @Query("DELETE FROM GeneratedDocument d WHERE d.expiresAt < :now")
    int deleteExpiredDocuments(@Param("now") LocalDateTime now);

    /**
     * 查询即将过期的文档（用于定时任务）
     */
    List<GeneratedDocument> findByExpiresAtBefore(LocalDateTime time);
}