package com.mediaproject.prompthubs.integration.mcp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class IssueApiKeyResponse {
    private UUID id;
    private String label;
    private String keyPlaintext;   // Returned ONCE at issuance, never again
    private String keyPrefix;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
