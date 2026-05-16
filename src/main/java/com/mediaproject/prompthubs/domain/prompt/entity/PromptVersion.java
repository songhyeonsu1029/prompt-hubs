package com.mediaproject.prompthubs.domain.prompt.entity;

import com.mediaproject.prompthubs.domain.account.entity.Account;
import com.mediaproject.prompthubs.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "prompt_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PromptVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prompt_id", nullable = false)
    private Prompt prompt;

    @Column(name = "version_number", nullable = false)
    private String versionNumber;

    @Column(name = "prompt_text", columnDefinition = "TEXT", nullable = false)
    private String promptText;

    @Column(name = "success_criteria", columnDefinition = "TEXT", nullable = false)
    private String successCriteria;

    @Column(name = "validation_method", columnDefinition = "TEXT", nullable = false)
    private String validationMethod;

    @Column(name = "change_note", columnDefinition = "TEXT")
    private String changeNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private Account createdBy;

    /**
     * In-place edit during the PENDING review window — replaces the body without creating a new
     * version row. Used by ReviewService.updateDraft.
     */
    public void updateDraftBody(String promptText, String successCriteria, String validationMethod) {
        if (promptText != null) this.promptText = promptText;
        if (successCriteria != null) this.successCriteria = successCriteria;
        if (validationMethod != null) this.validationMethod = validationMethod;
    }
}