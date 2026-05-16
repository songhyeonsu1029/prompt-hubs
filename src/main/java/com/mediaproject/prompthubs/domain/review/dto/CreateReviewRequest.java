package com.mediaproject.prompthubs.domain.review.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CreateReviewRequest {

    @NotNull(message = "Prompt version ID is required")
    private UUID promptVersionId;

    @NotNull(message = "Reviewer ID is required")
    private UUID reviewerId;
}