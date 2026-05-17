package com.mediaproject.prompthubs.integration.slack;

import com.mediaproject.prompthubs.domain.review.entity.PromptReview;
import com.mediaproject.prompthubs.integration.common.crypto.TokenCipher;
import com.mediaproject.prompthubs.integration.common.entity.IntegrationType;
import com.mediaproject.prompthubs.integration.common.entity.WorkspaceIntegration;
import com.mediaproject.prompthubs.integration.common.event.*;
import com.mediaproject.prompthubs.integration.common.repository.WorkspaceIntegrationRepository;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import com.slack.api.model.block.element.BlockElement;
import com.slack.api.model.block.element.ButtonElement;
import com.slack.api.model.block.ActionsBlock;
import com.slack.api.model.block.composition.PlainTextObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Translates domain events to Slack messages. Runs after the originating transaction commits.
 * Silently no-ops if the workspace has no active Slack integration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlackNotificationService {

    private final WorkspaceIntegrationRepository integrationRepository;
    private final SlackApiClient apiClient;
    private final SlackProperties properties;
    private final TokenCipher tokenCipher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewRequested(ReviewRequestedEvent event) {
        send(event.workspaceId(), WorkspaceIntegration::isSlackNotifyReviewRequested, integration -> {
            PromptReview review = event.review();
            String text = String.format("New Prompt Review requested by %s",
                    review.getRequester().getName());
            List<LayoutBlock> blocks = List.of(
                    section("*🔍 New Prompt Review request*\n*Title:* "
                            + review.getVersion().getPrompt().getTitle()
                            + "\n*Version:* v" + review.getVersion().getVersionNumber()
                            + "\n*Requester:* " + review.getRequester().getName()
                            + "\n*Reviewer:* <@" + review.getReviewer().getName() + ">"),
                    actionRow(
                            button("✅ Approve", "approve_review", review.getId().toString(), "primary"),
                            button("❌ Request Changes", "reject_review", review.getId().toString(), "danger"),
                            button("💬 Comment", "comment_review", review.getId().toString(), null)
                    )
            );
            apiClient.postMessage(tokenCipher.decrypt(integration.getAccessTokenEncrypted()),
                    integration.getSlackChannelId(), text, blocks);
        });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewCompleted(ReviewCompletedEvent event) {
        send(event.workspaceId(), WorkspaceIntegration::isSlackNotifyReviewCompleted, integration -> {
            PromptReview review = event.review();
            String emoji = event.finalStatus() == PromptReview.Status.APPROVED ? "✅ Approved" : "❌ Changes requested";
            String text = String.format("Review %s — *%s*",
                    emoji, review.getVersion().getPrompt().getTitle());
            apiClient.postMessage(tokenCipher.decrypt(integration.getAccessTokenEncrypted()),
                    integration.getSlackChannelId(), text, List.of(section(text)));
        });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPromptVersionCreated(PromptVersionCreatedEvent event) {
        send(event.workspaceId(), WorkspaceIntegration::isSlackNotifyVersionCreated, integration -> {
            String text = String.format("📝 *%s* updated to v%s",
                    event.prompt().getTitle(), event.version().getVersionNumber());
            apiClient.postMessage(tokenCipher.decrypt(integration.getAccessTokenEncrypted()),
                    integration.getSlackChannelId(), text, List.of(section(text)));
        });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlanLimitWarning(PlanLimitWarningEvent event) {
        send(event.workspaceId(), WorkspaceIntegration::isSlackNotifyPlanWarning, integration -> {
            String text = String.format("⚠️ %s usage %d/%d on Free plan — consider upgrading",
                    event.resource(), event.currentUsage(), event.limit());
            apiClient.postMessage(tokenCipher.decrypt(integration.getAccessTokenEncrypted()),
                    integration.getSlackChannelId(), text, List.of(section(text)));
        });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPrePromptingViolation(PrePromptingViolationEvent event) {
        send(event.workspaceId(), WorkspaceIntegration::isSlackNotifyPrePrompting, integration -> {
            String text = String.format("🚫 *%s* — review request rejected: %s",
                    event.promptTitle(), event.reason());
            if (event.externalUrl() != null) {
                text += "\n<" + event.externalUrl() + "|Open in Notion>";
            }
            apiClient.postMessage(tokenCipher.decrypt(integration.getAccessTokenEncrypted()),
                    integration.getSlackChannelId(), text, List.of(section(text)));
        });
    }

    private void send(UUID workspaceId,
                      java.util.function.Predicate<WorkspaceIntegration> enabledCheck,
                      java.util.function.Consumer<WorkspaceIntegration> action) {
        if (!properties.isEnabled()) {
            return;
        }
        Optional<WorkspaceIntegration> opt = integrationRepository
                .findByWorkspaceIdAndType(workspaceId, IntegrationType.SLACK);
        if (opt.isEmpty() || !opt.get().isActive()) {
            return;
        }
        WorkspaceIntegration integration = opt.get();
        if (integration.getSlackChannelId() == null || integration.getAccessTokenEncrypted() == null) {
            return;
        }
        if (!enabledCheck.test(integration)) {
            return;
        }
        try {
            action.accept(integration);
        } catch (Exception e) {
            log.warn("Slack notification failed for workspace {}: {}", workspaceId, e.getMessage());
        }
    }

    private static SectionBlock section(String markdown) {
        return SectionBlock.builder()
                .text(MarkdownTextObject.builder().text(markdown).build())
                .build();
    }

    private static ButtonElement button(String label, String actionId, String value, String style) {
        ButtonElement.ButtonElementBuilder b = ButtonElement.builder()
                .actionId(actionId)
                .text(PlainTextObject.builder().text(label).build())
                .value(value);
        if (style != null) {
            b.style(style);
        }
        return b.build();
    }

    private static ActionsBlock actionRow(ButtonElement... buttons) {
        List<BlockElement> elements = new java.util.ArrayList<>(buttons.length);
        java.util.Collections.addAll(elements, buttons);
        return ActionsBlock.builder().elements(elements).build();
    }
}
