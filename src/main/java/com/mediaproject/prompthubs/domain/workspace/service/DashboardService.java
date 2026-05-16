package com.mediaproject.prompthubs.domain.workspace.service;

import com.mediaproject.prompthubs.domain.log.entity.PromptLog;
import com.mediaproject.prompthubs.domain.log.repository.PromptLogRepository;
import com.mediaproject.prompthubs.domain.prompt.entity.Prompt;
import com.mediaproject.prompthubs.domain.prompt.repository.PromptRepository;
import com.mediaproject.prompthubs.domain.review.entity.PromptReview;
import com.mediaproject.prompthubs.domain.review.repository.ReviewRepository;
import com.mediaproject.prompthubs.domain.workspace.dto.DashboardResponse;
import com.mediaproject.prompthubs.domain.workspace.dto.DashboardResponse.ActivityItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final int RECENT_ACTIVITY_LIMIT = 10;

    private final PromptRepository promptRepository;
    private final PromptLogRepository logRepository;
    private final ReviewRepository reviewRepository;

    public DashboardResponse getDashboard(UUID workspaceId, UUID accountId) {
        long totalPrompts = promptRepository.countByWorkspaceId(workspaceId);
        long totalLogs = logRepository.countByWorkspaceId(workspaceId);
        long pendingReviews = reviewRepository.countPendingByWorkspaceIdAndReviewerId(workspaceId, accountId);

        List<ActivityItem> recentActivity = buildRecentActivity(workspaceId);

        return DashboardResponse.builder()
                .totalPrompts(totalPrompts)
                .totalLogs(totalLogs)
                .pendingReviews(pendingReviews)
                .recentActivity(recentActivity)
                .build();
    }

    private List<ActivityItem> buildRecentActivity(UUID workspaceId) {
        PageRequest limit = PageRequest.of(0, RECENT_ACTIVITY_LIMIT);

        List<ActivityItem> activities = new ArrayList<>();

        // Recent prompts
        List<Prompt> recentPrompts = promptRepository.findRecentByWorkspaceId(workspaceId, limit);
        for (Prompt p : recentPrompts) {
            activities.add(ActivityItem.builder()
                    .type("PROMPT_CREATED")
                    .title(p.getTitle())
                    .actor(p.getAuthor().getEmail())
                    .createdAt(p.getCreatedAt())
                    .build());
        }

        // Recent logs
        List<PromptLog> recentLogs = logRepository.findRecentByWorkspaceId(workspaceId, limit);
        for (PromptLog l : recentLogs) {
            activities.add(ActivityItem.builder()
                    .type("LOG_CREATED")
                    .title(l.getPromptText().length() > 50
                            ? l.getPromptText().substring(0, 50) + "..."
                            : l.getPromptText())
                    .actor(l.getAuthor().getEmail())
                    .createdAt(l.getCreatedAt())
                    .build());
        }

        // Recent reviews
        List<PromptReview> recentReviews = reviewRepository.findRecentByWorkspaceId(workspaceId, limit);
        for (PromptReview r : recentReviews) {
            String type = switch (r.getStatus()) {
                case APPROVED -> "REVIEW_APPROVED";
                case REJECTED -> "REVIEW_REJECTED";
                case PENDING -> "REVIEW_REQUESTED";
            };
            activities.add(ActivityItem.builder()
                    .type(type)
                    .title(r.getVersion().getPrompt().getTitle())
                    .actor(r.getStatus() == PromptReview.Status.PENDING
                            ? r.getRequester().getEmail()
                            : r.getReviewer().getEmail())
                    .createdAt(r.getStatus() == PromptReview.Status.PENDING
                            ? r.getCreatedAt()
                            : r.getReviewedAt())
                    .build());
        }

        // Sort by createdAt desc and take top 10
        activities.sort(Comparator.comparing(ActivityItem::getCreatedAt).reversed());

        return activities.size() > RECENT_ACTIVITY_LIMIT
                ? activities.subList(0, RECENT_ACTIVITY_LIMIT)
                : activities;
    }
}
