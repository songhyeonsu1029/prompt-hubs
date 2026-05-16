package com.mediaproject.prompthubs.domain.workspace.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class DashboardResponse {

    private long totalPrompts;
    private long totalLogs;
    private long pendingReviews;
    private List<ActivityItem> recentActivity;

    @Getter
    @Builder
    public static class ActivityItem {
        private String type;
        private String title;
        private String actor;
        private LocalDateTime createdAt;
    }
}