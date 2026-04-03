package com.ai.assistant.domain.repository;

import com.ai.assistant.domain.entity.ApiKeyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyConfigRepository extends JpaRepository<ApiKeyConfig, Long> {

    Optional<ApiKeyConfig> findByProvider(String provider);

    Optional<ApiKeyConfig> findByProviderAndIsActiveTrue(String provider);

    List<ApiKeyConfig> findByProviderType(String providerType);

    List<ApiKeyConfig> findByIsActiveTrue();
}
