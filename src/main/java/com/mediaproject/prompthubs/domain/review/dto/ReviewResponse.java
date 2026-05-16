package com.mediaproject.prompthubs.domain.review.dto;

import com.mediaproject.prompthubs.domain.review.entity.PromptReview;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ReviewResponse {

    private UUID id;
    private UUID workspaceId;
    private UUID versionId;
    private String versionNumber;
    private UUID promptId;
    private String promptTitle;
    private UUID requesterId;
    private String requesterName;
    private UUID reviewerId;
    private String reviewerName;
    private String status;
    private long unresolvedComments;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;

    public static ReviewResponse from(PromptReview review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .workspaceId(review.getWorkspace().getId())
                .versionId(review.getVersion().getId())
                .versionNumber(review.getVersion().getVersionNumber())
                .promptId(review.getVersion().getPrompt().getId())
                .promptTitle(review.getVersion().getPrompt().getTitle())
                .requesterId(review.getRequester().getId())
                .requesterName(review.getRequester().getName())
                .reviewerId(review.getReviewer().getId())
                .reviewerName(review.getReviewer().getName())
                .status(review.getStatus().name())
                .createdAt(review.getCreatedAt())
                .reviewedAt(review.getReviewedAt())
                .build();
    }

    public static ReviewResponse from(PromptReview review, long unresolvedComments) {
        return ReviewResponse.builder()
                .id(review.getId())
                .workspaceId(review.getWorkspace().getId())
                .versionId(review.getVersion().getId())
                .versionNumber(review.getVersion().getVersionNumber())
                .promptId(review.getVersion().getPrompt().getId())
                .promptTitle(review.getVersion().getPrompt().getTitle())
                .requesterId(review.getRequester().getId())
                .requesterName(review.getRequester().getName())
                .reviewerId(review.getReviewer().getId())
                .reviewerName(review.getReviewer().getName())
                .status(review.getStatus().name())
                .unresolvedComments(unresolvedComments)
                .createdAt(review.getCreatedAt())
                .reviewedAt(review.getReviewedAt())
                .build();
    }
}