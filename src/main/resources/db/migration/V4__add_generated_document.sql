-- AI 助手服务 - 文档生成记录表
-- Version: 1.0.0
-- Date: 2026-03-27
-- Description: 存储 AI 助手生成的文档记录（Excel/Word）

CREATE TABLE t_generated_document (
    id                  BIGINT UNSIGNED     PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    document_id         VARCHAR(64)         NOT NULL COMMENT '文档唯一标识(UUID)',
    document_type       VARCHAR(32)         NOT NULL COMMENT '文档类型: EXCEL, WORD',
    title               VARCHAR(256)         NOT NULL COMMENT '文档标题',
    file_name           VARCHAR(256)        NOT NULL COMMENT '文件名(带扩展名)',
    file_path           VARCHAR(512)        NOT NULL COMMENT '文件存储路径',
    file_size           BIGINT               COMMENT '文件大小(字节)',
    mime_type           VARCHAR(64)          NOT NULL DEFAULT 'application/octet-stream' COMMENT 'MIME类型',
    created_by          VARCHAR(64)          COMMENT '创建人ID',
    session_id          VARCHAR(64)          COMMENT '会话ID',
    expires_at          TIMESTAMP            NOT NULL COMMENT '过期时间(24小时后)',
    created_at          TIMESTAMP            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_document_id (document_id),
    INDEX idx_created_by (created_by),
    INDEX idx_session_id (session_id),
    INDEX idx_expires_at (expires_at),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生成的文档记录表';