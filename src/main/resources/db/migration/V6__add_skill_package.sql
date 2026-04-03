-- Skill Package 表
-- 存储 Skill 包的元数据，指向对象存储中的文件
-- Version: 1.0.0
-- Date: 2026-03-27

CREATE TABLE t_skill_package (
    id              BIGINT UNSIGNED     PRIMARY KEY AUTO_INCREMENT,
    skill_id        VARCHAR(64)         NOT NULL COMMENT '技能唯一标识',
    name            VARCHAR(128)        NOT NULL COMMENT '技能名称',
    description     TEXT                COMMENT '技能描述',
    storage_path    VARCHAR(512)        NOT NULL COMMENT '对象存储路径',
    version         VARCHAR(32)         DEFAULT '1.0.0' COMMENT '版本号',
    is_active       TINYINT             NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_by      VARCHAR(64)         COMMENT '创建人',
    created_at      TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_skill_id (skill_id),
    INDEX idx_is_active (is_active),
    INDEX idx_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技能包表';