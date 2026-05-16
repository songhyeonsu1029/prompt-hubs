package com.mediaproject.prompthubs.integration.common.event;

import com.mediaproject.prompthubs.domain.review.entity.PromptReview;

import java.util.UUID;

public record ReviewRequestedEvent(UUID workspaceId, UUID reviewId, PromptReview review) {
}
