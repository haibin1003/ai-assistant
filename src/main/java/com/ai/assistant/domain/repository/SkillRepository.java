package com.ai.assistant.domain.repository;

import com.ai.assistant.domain.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillRepository extends JpaRepository<Skill, Long> {

    Optional<Skill> findBySkillId(String skillId);

    Optional<Skill> findBySkillIdAndIsActiveTrue(String skillId);

    List<Skill> findByIsGlobalTrueAndIsActiveTrue();

    List<Skill> findByCreatedByAndIsActiveTrue(String createdBy);

    List<Skill> findByCreatedByAndIsGlobalFalseAndIsActiveTrue(String createdBy);

    List<Skill> findBySystemIdAndIsActiveTrue(String systemId);

    boolean existsBySkillId(String skillId);
}
