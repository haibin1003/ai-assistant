package com.ai.assistant.domain.repository;

import com.ai.assistant.domain.entity.ToolCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ToolCacheRepository extends JpaRepository<ToolCache, Long> {

    List<ToolCache> findBySystemId(String systemId);

    @Modifying
    void deleteBySystemId(String systemId);

    boolean existsBySystemId(String systemId);

    long countBySystemId(String systemId);
}
