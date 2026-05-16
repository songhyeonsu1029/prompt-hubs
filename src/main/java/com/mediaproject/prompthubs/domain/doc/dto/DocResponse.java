package com.mediaproject.prompthubs.domain.doc.dto;

import com.mediaproject.prompthubs.domain.doc.entity.Doc;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class DocResponse {

    private UUID id;
    private UUID workspaceId;
    private UUID authorId;
    private String authorName;
    private String title;
    private String content;
    private String category;
    @Setter
    private List<LinkedPromptInfo> linkedPrompts;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Builder
    public static class LinkedPromptInfo {
        private UUID id;
        private String title;
        private String status;
    }

    public static DocResponse from(Doc doc) {
        return DocResponse.builder()
                .id(doc.getId())
                .workspaceId(doc.getWorkspace().getId())
                .authorId(doc.getAuthor().getId())
                .authorName(doc.getAuthor().getName())
                .title(doc.getTitle())
                .content(doc.getContent())
                .category(doc.getCategory())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }
}