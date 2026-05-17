package com.mediaproject.prompthubs.integration.slack;

import com.mediaproject.prompthubs.domain.review.entity.PromptReview;
import com.mediaproject.prompthubs.domain.review.entity.ReviewComment;
import com.mediaproject.prompthubs.domain.review.repository.ReviewCommentRepository;
import com.mediaproject.prompthubs.domain.review.repository.ReviewRepository;
import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import com.mediaproject.prompthubs.integration.common.crypto.TokenCipher;
import com.mediaproject.prompthubs.integration.common.entity.IntegrationType;
import com.mediaproject.prompthubs.integration.common.entity.WorkspaceIntegration;
import com.mediaproject.prompthubs.integration.common.repository.WorkspaceIntegrationRepository;
import com.slack.api.model.block.InputBlock;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import com.slack.api.model.block.composition.PlainTextObject;
import com.slack.api.model.block.element.PlainTextInputElement;
import com.slack.api.model.view.View;
import com.slack.api.model.view.ViewClose;
import com.slack.api.model.view.ViewSubmit;
import com.slack.api.model.view.ViewTitle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles button clicks and modal submissions from Slack interactive messages.
 * Reads payload JSON, dispatches to domain services, and returns a short response message.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlackInteractionService {

    private static final String COMMENT_MODAL_CALLBACK = "review_comment";
    private static final String COMMENT_INPUT_BLOCK = "comment_block";
    private static final String COMMENT_INPUT_ACTION = "comment_input";

    private final ReviewRepository reviewRepository;
    private final ReviewCommentRepository reviewCommentRepository;
    private final WorkspaceIntegrationRepository integrationRepository;
    private final SlackApiClient apiClient;
    private final TokenCipher tokenCipher;
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
        String teamId = root.path("team").path("id").asText(null);
        String channel = root.path("channel").path("id").asText(null);
        String messageTs = root.path("message").path("ts").asText(null);
        String slackUser = root.path("user").path("name").asText("someone");

        if ("approve_review".equals(actionId)) {
            return processReview(UUID.fromString(value), true, teamId, channel, messageTs, slackUser);
        }
        if ("reject_review".equals(actionId)) {
            return processReview(UUID.fromString(value), false, teamId, channel, messageTs, slackUser);
        }
        if ("comment_review".equals(actionId)) {
            openCommentModal(root, value, teamId, channel, messageTs);
            return "";
        }
        return "Unhandled action: " + actionId;
    }

    private void openCommentModal(JsonNode root,
                                  String reviewId,
                                  String teamId,
                                  String channelId,
                                  String messageTs) {
        String triggerId = root.path("trigger_id").asText(null);
        if (triggerId == null || teamId == null) {
            log.warn("Missing trigger_id/team_id in comment_review payload");
            return;
        }
        Optional<WorkspaceIntegration> opt = integrationRepository
                .findBySlackTeamIdAndType(teamId, IntegrationType.SLACK);
        if (opt.isEmpty() || opt.get().getAccessTokenEncrypted() == null) {
            return;
        }
        String token = tokenCipher.decrypt(opt.get().getAccessTokenEncrypted());

        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("reviewId", reviewId);
        if (channelId != null) meta.put("channelId", channelId);
        if (messageTs != null) meta.put("messageTs", messageTs);

        View view = View.builder()
                .type("modal")
                .callbackId(COMMENT_MODAL_CALLBACK)
                .privateMetadata(meta.toString())
                .title(ViewTitle.builder().type("plain_text").text("Review Comment").build())
                .submit(ViewSubmit.builder().type("plain_text").text("Submit").build())
                .close(ViewClose.builder().type("plain_text").text("Cancel").build())
                .blocks(List.<LayoutBlock>of(
                        InputBlock.builder()
                                .blockId(COMMENT_INPUT_BLOCK)
                                .label(PlainTextObject.builder().text("코멘트").build())
                                .element(PlainTextInputElement.builder()
                                        .actionId(COMMENT_INPUT_ACTION)
                                        .multiline(true)
                                        .placeholder(PlainTextObject.builder()
                                                .text("리뷰에 남길 의견을 적어주세요").build())
                                        .build())
                                .build()
                ))
                .build();

        try {
            apiClient.openModal(token, triggerId, view);
        } catch (Exception e) {
            log.warn("Failed to open Slack comment modal: {}", e.getMessage());
        }
    }

    private String processReview(UUID reviewId,
                                 boolean approve,
                                 String teamId,
                                 String channel,
                                 String messageTs,
                                 String slackUser) {
        PromptReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Review not found"));
        if (review.getStatus() != PromptReview.Status.PENDING) {
            return "Review already processed.";
        }
        if (approve) {
            review.approve();
            review.getVersion().getPrompt().updateStatus(
                    com.mediaproject.prompthubs.domain.prompt.entity.Prompt.Status.APPROVED);
        } else {
            review.reject();
            review.getVersion().getPrompt().updateStatus(
                    com.mediaproject.prompthubs.domain.prompt.entity.Prompt.Status.DRAFT);
        }

        updateOriginalMessage(teamId, channel, messageTs, review, approve, slackUser);
        return approve ? "✅ Review approved." : "❌ Review rejected.";
    }

    private void updateOriginalMessage(String teamId,
                                       String channel,
                                       String messageTs,
                                       PromptReview review,
                                       boolean approved,
                                       String slackUser) {
        if (teamId == null || channel == null || messageTs == null) {
            return;
        }
        Optional<WorkspaceIntegration> opt = integrationRepository
                .findBySlackTeamIdAndType(teamId, IntegrationType.SLACK);
        if (opt.isEmpty() || opt.get().getAccessTokenEncrypted() == null) {
            return;
        }
        try {
            WorkspaceIntegration integration = opt.get();
            String token = tokenCipher.decrypt(integration.getAccessTokenEncrypted());

            String statusLine = approved
                    ? "*✅ Approved* by <@" + slackUser + "> · " + formatTime(review)
                    : "*❌ Changes requested* by <@" + slackUser + "> · " + formatTime(review);

            String summary = "*" + review.getVersion().getPrompt().getTitle()
                    + "* (v" + review.getVersion().getVersionNumber() + ")"
                    + "\nRequester: " + review.getRequester().getName()
                    + "\nReviewer: " + review.getReviewer().getName()
                    + "\n\n" + statusLine;

            List<LayoutBlock> blocks = List.of(
                    SectionBlock.builder()
                            .text(MarkdownTextObject.builder().text(summary).build())
                            .build()
            );
            String fallback = approved
                    ? "Approved: " + review.getVersion().getPrompt().getTitle()
                    : "Changes requested: " + review.getVersion().getPrompt().getTitle();
            apiClient.updateMessage(token, channel, messageTs, fallback, blocks);
        } catch (Exception e) {
            // 메시지 업데이트 실패는 사용자 액션 자체를 막지 않는다 — 로그만 남기고 무시
            log.warn("Failed to update original Slack message: {}", e.getMessage());
        }
    }

    private String formatTime(PromptReview review) {
        return review.getReviewedAt() == null
                ? "방금"
                : review.getReviewedAt().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }

    private String handleViewSubmission(JsonNode root) {
        String callbackId = root.path("view").path("callback_id").asText("");
        if (!COMMENT_MODAL_CALLBACK.equals(callbackId)) {
            return "";
        }

        String slackUser = root.path("user").path("name").asText("someone");
        String teamId = root.path("team").path("id").asText(null);

        JsonNode meta;
        try {
            meta = objectMapper.readTree(root.path("view").path("private_metadata").asText("{}"));
        } catch (Exception e) {
            log.warn("Failed to parse view private_metadata", e);
            return "";
        }
        String reviewIdStr = meta.path("reviewId").asText(null);
        String channelId = meta.path("channelId").asText(null);
        String messageTs = meta.path("messageTs").asText(null);
        if (reviewIdStr == null) {
            return "";
        }

        String commentText = root.path("view").path("state").path("values")
                .path(COMMENT_INPUT_BLOCK).path(COMMENT_INPUT_ACTION).path("value").asText("").trim();
        if (commentText.isEmpty()) {
            return "";
        }

        PromptReview review = reviewRepository.findById(UUID.fromString(reviewIdStr))
                .orElse(null);
        if (review == null) {
            log.warn("Slack comment for unknown review {}", reviewIdStr);
            return "";
        }

        // Slack user → Account 매핑이 아직 없으므로 author는 리뷰의 reviewer로 고정,
        // content 앞에 슬랙 사용자 이름을 prepend해서 출처를 보존한다.
        ReviewComment comment = ReviewComment.builder()
                .review(review)
                .author(review.getReviewer())
                .content("[Slack · @" + slackUser + "] " + commentText)
                .build();
        reviewCommentRepository.save(comment);
        log.info("Slack comment saved for review {} by @{}", review.getId(), slackUser);

        // 채널의 원본 카드 아래에 스레드 답글로 노출 (다른 팀원도 볼 수 있게)
        if (teamId != null && channelId != null && messageTs != null) {
            try {
                Optional<WorkspaceIntegration> opt = integrationRepository
                        .findBySlackTeamIdAndType(teamId, IntegrationType.SLACK);
                if (opt.isPresent() && opt.get().getAccessTokenEncrypted() != null) {
                    String token = tokenCipher.decrypt(opt.get().getAccessTokenEncrypted());
                    String threadText = "💬 *<@" + slackUser + ">*\n" + commentText;
                    apiClient.postThreadReply(token, channelId, messageTs, threadText);
                }
            } catch (Exception e) {
                log.warn("Failed to post Slack thread reply: {}", e.getMessage());
            }
        }

        return "";
    }
}
