package com.mediaproject.prompthubs.domain.prompt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class CreateVersionRequest {

    @NotBlank(message = "Prompt text is required")
    private String promptText;

    @NotBlank(message = "Success criteria is required")
    private String successCriteria;

    @NotBlank(message = "Validation method is required")
    private String validationMethod;

    private String changeNote;

    /** Reviewer to send the auto-created PENDING review to. */
    @NotNull(message = "Reviewer is required")
    private UUID reviewerId;
}