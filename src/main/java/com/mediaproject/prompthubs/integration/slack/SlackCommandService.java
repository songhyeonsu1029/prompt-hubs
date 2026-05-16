package com.mediaproject.prompthubs.integration.slack;

import com.mediaproject.prompthubs.domain.prompt.entity.Prompt;
import com.mediaproject.prompthubs.domain.prompt.repository.PromptRepository;
import com.mediaproject.prompthubs.domain.review.entity.PromptReview;
import com.mediaproject.prompthubs.domain.review.repository.ReviewRepository;
import com.mediaproject.prompthubs.integration.common.entity.IntegrationType;
import com.mediaproject.prompthubs.integration.common.entity.WorkspaceIntegration;
import com.mediaproject.prompthubs.integration.common.repository.WorkspaceIntegrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SlackCommandService {

    private final WorkspaceIntegrationRepository integrationRepository;
    private final PromptRepository promptRepository;
    private final ReviewRepository reviewRepository;

    /**
     * Returns a Slack-formatted text response (Block Kit out of scope here for brevity).
     */
    public String handleCommand(String teamId, String command, String text) {
        Optional<WorkspaceIntegration> opt = integrationRepository
                .findBySlackTeamIdAndType(teamId, IntegrationType.SLACK);
        if (opt.isEmpty() || !opt.get().isActive()) {
            return ":warning: This Slack workspace isn't connected to a Prompt Hubs workspace.";
        }
        WorkspaceIntegration integration = opt.get();
        java.util.UUID workspaceId = integration.getWorkspace().getId();

        String subcommand = text == null ? "" : text.trim().split("\\s+", 2)[0].toLowerCase();
        String args = text == null || !text.contains(" ") ? "" : text.substring(text.indexOf(' ') + 1).trim();

        return switch (subcommand) {
            case "search" -> searchPrompts(workspaceId, args);
            case "recent" -> recentPrompts(workspaceId);
            case "status" -> myReviews(workspaceId);
            case "" -> usage();
            default -> "Unknown subcommand. Try `/prompt search <keyword>`, `/prompt recent`, `/prompt status`.";
        };
    }

    private String searchPrompts(java.util.UUID workspaceId, String keyword) {
        if (keyword.isBlank()) {
            return "Usage: `/prompt search <keyword>`";
        }
        var page = promptRepository.findByWorkspaceIdAndKeyword(
                workspaceId, keyword,
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "updatedAt")));
        if (page.isEmpty()) {
            return "No matching prompts.";
        }
        StringBuilder sb = new StringBuilder("*Search results:*\n");
        page.forEach(p -> sb.append("• ").append(p.getTitle())
                .append(" — `").append(p.getStatus().name()).append("`\n"));
        return sb.toString();
    }

    private String recentPrompts(java.util.UUID workspaceId) {
        var page = promptRepository.findByWorkspaceIdAndStatus(
                workspaceId, Prompt.Status.APPROVED,
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "updatedAt")));
        if (page.isEmpty()) {
            return "No approved prompts yet.";
        }
        StringBuilder sb = new StringBuilder("*Recent approved prompts:*\n");
        page.forEach(p -> sb.append("• ").append(p.getTitle()).append("\n"));
        return sb.toString();
    }

    private String myReviews(java.util.UUID workspaceId) {
        var page = reviewRepository.findByWorkspaceIdAndStatus(
                workspaceId, PromptReview.Status.PENDING,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));
        if (page.isEmpty()) {
            return "No pending reviews in this workspace.";
        }
        StringBuilder sb = new StringBuilder("*Pending reviews:*\n");
        page.forEach(r -> sb.append("• ")
                .append(r.getVersion().getPrompt().getTitle())
                .append(" — reviewer: ").append(r.getReviewer().getName())
                .append("\n"));
        return sb.toString();
    }

    private String usage() {
        return "*Available commands:*\n"
                + "• `/prompt search <keyword>` — search approved prompts\n"
                + "• `/prompt recent` — last 5 approved prompts\n"
                + "• `/prompt status` — pending reviews in this workspace";
    }
}
