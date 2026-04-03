package com.ai.assistant.domain.repository;

import com.ai.assistant.domain.entity.RegisteredSystem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegisteredSystemRepository extends JpaRepository<RegisteredSystem, Long> {

    Optional<RegisteredSystem> findBySystemId(String systemId);

    Optional<RegisteredSystem> findBySystemIdAndIsActiveTrue(String systemId);

    Optional<RegisteredSystem> findByToolPrefixAndIsActiveTrue(String toolPrefix);

    List<RegisteredSystem> findByIsActiveTrue();

    boolean existsBySystemId(String systemId);
}
