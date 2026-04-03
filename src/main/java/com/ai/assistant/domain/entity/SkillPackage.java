package com.ai.assistant.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Skill 包实体 - 存储标准结构 Skill 的元数据
 */
@Entity
@Table(name = "t_skill_package")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 技能唯一标识（目录名）
     */
    @Column(name = "skill_id", unique = true, nullable = false, length = 64)
    private String skillId;

    /**
     * 技能显示名称
     */
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /**
     * 技能描述
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 对象存储路径（如：skills/software-report/）
     */
    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    /**
     * 版本号
     */
    @Column(name = "version", length = 32)
    @Builder.Default
    private String version = "1.0.0";

    /**
     * 是否启用
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 创建人用户ID
     */
    @Column(name = "created_by", length = 64)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}