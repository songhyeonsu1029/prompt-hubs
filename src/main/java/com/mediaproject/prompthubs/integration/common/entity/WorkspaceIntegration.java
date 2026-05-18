package com.mediaproject.prompthubs.integration.common.entity;

import com.mediaproject.prompthubs.domain.workspace.entity.Workspace;
import com.mediaproject.prompthubs.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "workspace_integrations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_workspace_integration_type",
                        columnNames = {"workspace_id", "type"}),
                @UniqueConstraint(name = "uk_workspace_integration_slack_team",
                        columnNames = {"slack_team_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class WorkspaceIntegration extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IntegrationType type;

    @Column(name = "access_token_encrypted", columnDefinition = "TEXT")
    private String accessTokenEncrypted;

    @Column(name = "refresh_token_encrypted", columnDefinition = "TEXT")
    private String refreshTokenEncrypted;

    // Notion-specific
    @Column(name = "notion_logs_db_id")
    private String notionLogsDbId;

    @Column(name = "notion_docs_db_id")
    private String notionDocsDbId;

    @Column(name = "notion_prompts_db_id")
    private String notionPromptsDbId;

    @Column(name = "notion_workspace_id")
    private String notionWorkspaceId;

    @Column(name = "notion_parent_page_id")
    private String notionParentPageId;

    // Slack-specific
    @Column(name = "slack_team_id")
    private String slackTeamId;

    @Column(name = "slack_channel_id")
    private String slackChannelId;

    @Column(name = "slack_channel_name")
    private String slackChannelName;

    @Column(name = "slack_bot_user_id")
    private String slackBotUserId;

    @Column(name = "slack_notify_review_requested", nullable = false)
    @Builder.Default
    private boolean slackNotifyReviewRequested = true;

    @Column(name = "slack_notify_review_completed", nullable = false)
    @Builder.Default
    private boolean slackNotifyReviewCompleted = true;

    @Column(name = "slack_notify_version_created", nullable = false)
    @Builder.Default
    private boolean slackNotifyVersionCreated = true;

    @Column(name = "slack_notify_plan_warning", nullable = false)
    @Builder.Default
    private boolean slackNotifyPlanWarning = true;

    @Column(name = "slack_notify_pre_prompting", nullable = false)
    @Builder.Default
    private boolean slackNotifyPrePrompting = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "connected_at")
    private LocalDateTime connectedAt;

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    public void updateTokens(String accessTokenEncrypted, String refreshTokenEncrypted) {
        this.accessTokenEncrypted = accessTokenEncrypted;
        if (refreshTokenEncrypted != null) {
            this.refreshTokenEncrypted = refreshTokenEncrypted;
        }
    }

    public void updateNotionDatabases(String logsDbId, String docsDbId, String promptsDbId) {
        this.notionLogsDbId = logsDbId;
        this.notionDocsDbId = docsDbId;
        this.notionPromptsDbId = promptsDbId;
    }

    public void updateNotionMeta(String notionWorkspaceId) {
        this.notionWorkspaceId = notionWorkspaceId;
    }

    public void updateNotionParentPage(String parentPageId) {
        this.notionParentPageId = parentPageId;
    }

    public void updateSlackMeta(String teamId, String channelId, String botUserId) {
        this.slackTeamId = teamId;
        if (channelId != null) {
            this.slackChannelId = channelId;
        }
        this.slackBotUserId = botUserId;
    }

    public void updateSlackChannel(String channelId, String channelName) {
        this.slackChannelId = channelId;
        this.slackChannelName = channelName;
    }

    public void updateSlackNotificationFlags(
            boolean reviewRequested,
            boolean reviewCompleted,
            boolean versionCreated,
            boolean planWarning,
            boolean prePrompting) {
        this.slackNotifyReviewRequested = reviewRequested;
        this.slackNotifyReviewCompleted = reviewCompleted;
        this.slackNotifyVersionCreated = versionCreated;
        this.slackNotifyPlanWarning = planWarning;
        this.slackNotifyPrePrompting = prePrompting;
    }

    public void touchSync() {
        this.lastSyncAt = LocalDateTime.now();
    }

    public void deactivate() {
        this.active = false;
    }

    public void resetNotion() {
        this.accessTokenEncrypted = null;
        this.refreshTokenEncrypted = null;
        this.notionLogsDbId = null;
        this.notionDocsDbId = null;
        this.notionPromptsDbId = null;
        this.notionWorkspaceId = null;
        this.notionParentPageId = null;
        this.lastSyncAt = null;
        this.connectedAt = null;
    }

    public void markConnected() {
        this.connectedAt = LocalDateTime.now();
        this.active = true;
    }
}
