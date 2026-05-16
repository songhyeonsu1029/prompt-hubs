package com.mediaproject.prompthubs.domain.prompt.dto;

import com.mediaproject.prompthubs.domain.prompt.entity.Prompt;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class PromptListResponse {

    private UUID id;
    private String title;
    private String category;
    private List<String> tags;
    private String status;
    private String authorName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PromptListResponse from(Prompt prompt) {
        return PromptListResponse.builder()
                .id(prompt.getId())
                .title(prompt.getTitle())
                .category(prompt.getCategory())
                .tags(prompt.getTags())
                .status(prompt.getStatus().name())
                .authorName(prompt.getAuthor().getName())
                .createdAt(prompt.getCreatedAt())
                .updatedAt(prompt.getUpdatedAt())
                .build();
    }
}