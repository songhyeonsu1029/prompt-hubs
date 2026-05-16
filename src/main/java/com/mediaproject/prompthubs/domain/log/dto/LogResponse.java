package com.mediaproject.prompthubs.domain.log.dto;

import com.mediaproject.prompthubs.domain.log.entity.PromptLog;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
public class LogResponse {

    private UUID id;
    private UUID workspaceId;
    private UUID authorId;
    private String authorName;
    private String promptText;
    private String modelUsed;
    private String resultSummary;
    private String resultBody;
    private String status;
    private List<String> tags;
    private UUID sessionId;
    private String idempotencyKey;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static LogResponse from(PromptLog log) {
        return LogResponse.builder()
                .id(log.getId())
                .workspaceId(log.getWorkspace().getId())
                .authorId(log.getAuthor().getId())
                .authorName(log.getAuthor().getName())
                .promptText(log.getPromptText())
                .modelUsed(log.getModelUsed())
                .resultSummary(log.getResultSummary())
                .resultBody(log.getResultBody())
                .status(log.getStatus().name())
                .tags(log.getTags())
                .sessionId(log.getSessionId())
                .idempotencyKey(log.getIdempotencyKey())
                .metadata(log.getMetadata())
                .createdAt(log.getCreatedAt())
                .updatedAt(log.getUpdatedAt())
                .build();
    }
}
