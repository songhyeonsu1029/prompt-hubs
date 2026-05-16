package com.mediaproject.prompthubs.integration.common.repository;

import com.mediaproject.prompthubs.integration.common.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    List<ApiKey> findByWorkspaceId(UUID workspaceId);

    List<ApiKey> findByKeyPrefixAndActiveTrue(String keyPrefix);
}
