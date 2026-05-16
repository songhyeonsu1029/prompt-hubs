package com.mediaproject.prompthubs.domain.prompt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class PromoteLogRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String category;

    private List<String> tags;

    // Pre-prompting required fields
    @NotBlank(message = "Success criteria is required")
    private String successCriteria;

    @NotBlank(message = "Validation method is required")
    private String validationMethod;

    // Optional: override promptText from log
    private String promptText;

    /** Reviewer to send the auto-created PENDING review to. */
    @NotNull(message = "Reviewer is required")
    private UUID reviewerId;
}