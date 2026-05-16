package com.mediaproject.prompthubs.integration.slack;

import com.mediaproject.prompthubs.domain.review.entity.PromptReview;
import com.mediaproject.prompthubs.domain.review.repository.ReviewRepository;
import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Handles button clicks and modal submissions from Slack interactive messages.
 * Reads payload JSON, dispatches to domain services, and returns a short response message.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlackInteractionService {

    private final ReviewRepository reviewRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public String handle(String payloadJson) {
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            String type = root.path("type").asText();
            if ("block_actions".equals(type)) {
                return handleBlockAction(root);
            }
            if ("view_submission".equals(type)) {
                return handleViewSubmission(root);
            }
            return "";
        } catch (Exception e) {
            log.error("Failed to handle Slack interaction", e);
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, e.getMessage());
        }
    }

    private String handleBlockAction(JsonNode root) {
        JsonNode action = root.path("actions").path(0);
        String actionId = action.path("action_id").asText();
        String value = action.path("value").asText();

        if ("approve_review".equals(actionId)) {
            return approveReview(UUID.fromString(value));
        }
        if ("reject_review".equals(actionId)) {
            return rejectReview(UUID.fromString(value));
        }
        if ("comment_review".equals(actionId)) {
            return "Open the review in Prompt Hubs to comment.";
        }
        return "Unhandled action: " + actionId;
    }

    private String approveReview(UUID reviewId) {
        PromptReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Review not found"));
        if (review.getStatus() != PromptReview.Status.PENDING) {
            return "Review already processed.";
        }
        review.approve();
        review.getVersion().getPrompt().updateStatus(
                com.mediaproject.prompthubs.domain.prompt.entity.Prompt.Status.APPROVED);
        return "✅ Review approved.";
    }

    private String rejectReview(UUID reviewId) {
        PromptReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Review not found"));
        if (review.getStatus() != PromptReview.Status.PENDING) {
            return "Review already processed.";
        }
        review.reject();
        review.getVersion().getPrompt().updateStatus(
                com.mediaproject.prompthubs.domain.prompt.entity.Prompt.Status.DRAFT);
        return "❌ Review rejected.";
    }

    private String handleViewSubmission(JsonNode root) {
        // Extension point for modal-driven flows (e.g. /prompt log).
        return "";
    }
}
