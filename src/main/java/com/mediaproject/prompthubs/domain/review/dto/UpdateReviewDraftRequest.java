package com.mediaproject.prompthubs.domain.review.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Partial edit of the Prompt + PromptVersion attached to a PENDING review.
 *
 * Any non-null field overwrites the matching field on the prompt or version. The
 * version body is NOT versioned on save — this is the same draft the review is
 * pointing at, polished in place before approval.
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateReviewDraftRequest {

    // Prompt-level fields
    private String title;
    private String category;
    private List<String> tags;

    // Version-level fields
    private String promptText;
    private String successCriteria;
    private String validationMethod;
}
