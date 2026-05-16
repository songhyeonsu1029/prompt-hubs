package com.mediaproject.prompthubs.domain.log.dto;

import com.mediaproject.prompthubs.domain.log.entity.PromptLog;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class CreateLogRequest {

    @NotBlank(message = "Prompt text is required")
    private String promptText;

    @NotBlank(message = "Model used is required")
    private String modelUsed;

    private String resultSummary;

    /**
     * Full rendered assistant turn (markdown — text, code blocks, tool output).
     */
    private String resultBody;

    @NotNull(message = "Status is required")
    private PromptLog.Status status;

    private List<String> tags;

    private UUID sessionId;

    @Size(max = 64)
    private String idempotencyKey;

    private Map<String, Object> metadata;
}