package com.mediaproject.prompthubs.integration.mcp.dto;

import com.mediaproject.prompthubs.integration.common.entity.ApiKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ApiKeyListResponse {
    private UUID id;
    private String label;
    private String keyPrefix;
    private boolean active;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;

    public static ApiKeyListResponse from(ApiKey apiKey) {
        return ApiKeyListResponse.builder()
                .id(apiKey.getId())
                .label(apiKey.getLabel())
                .keyPrefix(apiKey.getKeyPrefix())
                .active(apiKey.isActive())
                .expiresAt(apiKey.getExpiresAt())
                .lastUsedAt(apiKey.getLastUsedAt())
                .createdAt(apiKey.getCreatedAt())
                .build();
    }
}
