package com.ai.assistant.domain.repository;

import com.ai.assistant.domain.entity.SkillPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Skill 包 Repository
 */
@Repository
public interface SkillPackageRepository extends JpaRepository<SkillPackage, Long> {

    Optional<SkillPackage> findBySkillId(String skillId);

    Optional<SkillPackage> findBySkillIdAndIsActiveTrue(String skillId);

    List<SkillPackage> findByIsActiveTrue();

    List<SkillPackage> findByCreatedBy(String createdBy);

    boolean existsBySkillId(String skillId);
}