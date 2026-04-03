package com.ai.assistant.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 生成的文档记录
 */
@Entity
@Table(name = "t_generated_document")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 文档唯一标识(UUID)
     */
    @Column(name = "document_id", unique = true, nullable = false, length = 64)
    private String documentId;

    /**
     * 文档类型: EXCEL, WORD
     */
    @Column(name = "document_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private DocumentType documentType;

    /**
     * 文档标题
     */
    @Column(name = "title", nullable = false, length = 256)
    private String title;

    /**
     * 文件名(带扩展名)
     */
    @Column(name = "file_name", nullable = false, length = 256)
    private String fileName;

    /**
     * 文件存储路径
     */
    @Column(name = "file_path", nullable = false, length = 512)
    private String filePath;

    /**
     * 文件大小(字节)
     */
    @Column(name = "file_size")
    private Long fileSize;

    /**
     * MIME类型
     */
    @Column(name = "mime_type", length = 256)
    private String mimeType;

    /**
     * 创建人ID
     */
    @Column(name = "created_by", length = 64)
    private String createdBy;

    /**
     * 会话ID
     */
    @Column(name = "session_id", length = 64)
    private String sessionId;

    /**
     * 过期时间(24小时后)
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 文档类型枚举
     */
    public enum DocumentType {
        EXCEL,
        WORD,
        MARKDOWN
    }

    /**
     * 检查文档是否过期
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}