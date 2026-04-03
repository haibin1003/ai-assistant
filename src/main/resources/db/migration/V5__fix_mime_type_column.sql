-- AI 助手服务 - 文档生成记录表
-- Version: 1.0.1
-- Date: 2026-03-27
-- Description: 存储 AI 助手生成的文档记录（Excel/Word）
-- Fix: 增加 mime_type 字段长度

ALTER TABLE t_generated_document MODIFY COLUMN mime_type VARCHAR(256) DEFAULT 'application/octet-stream' COMMENT 'MIME类型';