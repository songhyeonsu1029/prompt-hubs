package com.mediaproject.prompthubs.domain.prompt.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class CreatePromptRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String category;

    private List<String> tags;

    // Pre-prompting 3 required fields
    @NotBlank(message = "Success criteria is required")
    private String successCriteria;

    @NotBlank(message = "Validation method is required")
    private String validationMethod;

    @NotBlank(message = "Prompt text is required")
    private String promptText;

    private String changeNote;

    private List<UUID> linkedDocIds;

    /** Reviewer to send the auto-created PENDING review to. */
    @jakarta.validation.constraints.NotNull(message = "Reviewer is required")
    private UUID reviewerId;
}
