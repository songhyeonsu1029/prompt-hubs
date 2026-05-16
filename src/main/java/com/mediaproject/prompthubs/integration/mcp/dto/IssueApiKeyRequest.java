package com.mediaproject.prompthubs.integration.mcp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class IssueApiKeyRequest {

    @NotBlank(message = "Label is required")
    @Size(max = 100)
    private String label;

    private LocalDateTime expiresAt;
}
