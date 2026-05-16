package com.mediaproject.prompthubs.domain.review.dto;

import com.mediaproject.prompthubs.domain.review.entity.PromptReview;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateReviewRequest {

    @NotNull(message = "Status is required")
    private PromptReview.Status status;
}