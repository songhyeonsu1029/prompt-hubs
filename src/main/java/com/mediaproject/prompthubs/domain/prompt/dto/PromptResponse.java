package com.mediaproject.prompthubs.domain.prompt.dto;

import com.mediaproject.prompthubs.domain.prompt.entity.Prompt;
import com.mediaproject.prompthubs.domain.prompt.entity.PromptVersion;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class PromptResponse {

    private UUID id;
    private UUID workspaceId;
    private UUID authorId;
    private String authorName;
    private String title;
    private String category;
    private List<String> tags;
    private String status;
    private UUID sourceLogId;
    private UUID currentVersionId;
    private VersionInfo currentVersion;
    @Setter
    private List<LinkedDocInfo> linkedDocs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Builder
    public static class LinkedDocInfo {
        private UUID id;
        private String title;
        private String category;
    }

    @Getter
    @Builder
    public static class VersionInfo {
        private UUID id;
        private String versionNumber;
        private String promptText;
        private String successCriteria;
        private String validationMethod;
        private String changeNote;
        private String createdByName;
        private LocalDateTime createdAt;

        public static VersionInfo from(PromptVersion version) {
            return VersionInfo.builder()
                    .id(version.getId())
                    .versionNumber(version.getVersionNumber())
                    .promptText(version.getPromptText())
                    .successCriteria(version.getSuccessCriteria())
                    .validationMethod(version.getValidationMethod())
                    .changeNote(version.getChangeNote())
                    .createdByName(version.getCreatedBy().getName())
                    .createdAt(version.getCreatedAt())
                    .build();
        }
    }

    public static PromptResponse from(Prompt prompt) {
        return PromptResponse.builder()
                .id(prompt.getId())
                .workspaceId(prompt.getWorkspace().getId())
                .authorId(prompt.getAuthor().getId())
                .authorName(prompt.getAuthor().getName())
                .title(prompt.getTitle())
                .category(prompt.getCategory())
                .tags(prompt.getTags())
                .status(prompt.getStatus().name())
                .sourceLogId(prompt.getSourceLogId())
                .currentVersionId(prompt.getCurrentVersionId())
                .createdAt(prompt.getCreatedAt())
                .updatedAt(prompt.getUpdatedAt())
                .build();
    }

    public static PromptResponse from(Prompt prompt, PromptVersion currentVersion) {
        return PromptResponse.builder()
                .id(prompt.getId())
                .workspaceId(prompt.getWorkspace().getId())
                .authorId(prompt.getAuthor().getId())
                .authorName(prompt.getAuthor().getName())
                .title(prompt.getTitle())
                .category(prompt.getCategory())
                .tags(prompt.getTags())
                .status(prompt.getStatus().name())
                .sourceLogId(prompt.getSourceLogId())
                .currentVersionId(prompt.getCurrentVersionId())
                .currentVersion(currentVersion != null ? VersionInfo.from(currentVersion) : null)
                .createdAt(prompt.getCreatedAt())
                .updatedAt(prompt.getUpdatedAt())
                .build();
    }
}