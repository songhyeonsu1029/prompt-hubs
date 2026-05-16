package com.mediaproject.prompthubs.domain.prompt.dto;

import com.mediaproject.prompthubs.domain.prompt.entity.PromptVersion;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class VersionResponse {

    private UUID id;
    private UUID promptId;
    private String versionNumber;
    private String promptText;
    private String successCriteria;
    private String validationMethod;
    private String changeNote;
    private UUID createdById;
    private String createdByName;
    private LocalDateTime createdAt;

    public static VersionResponse from(PromptVersion version) {
        return VersionResponse.builder()
                .id(version.getId())
                .promptId(version.getPrompt().getId())
                .versionNumber(version.getVersionNumber())
                .promptText(version.getPromptText())
                .successCriteria(version.getSuccessCriteria())
                .validationMethod(version.getValidationMethod())
                .changeNote(version.getChangeNote())
                .createdById(version.getCreatedBy().getId())
                .createdByName(version.getCreatedBy().getName())
                .createdAt(version.getCreatedAt())
                .build();
    }
}