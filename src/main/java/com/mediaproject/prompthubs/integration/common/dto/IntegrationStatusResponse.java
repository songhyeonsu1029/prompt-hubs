package com.mediaproject.prompthubs.integration.common.dto;

import com.mediaproject.prompthubs.integration.common.entity.IntegrationType;
import com.mediaproject.prompthubs.integration.common.entity.WorkspaceIntegration;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class IntegrationStatusResponse {

    private IntegrationType type;
    private boolean active;
    private boolean configured;
    private LocalDateTime connectedAt;
    private LocalDateTime lastSyncAt;
    private String slackTeamId;
    private String slackChannelId;
    private String notionLogsDbId;
    private String notionDocsDbId;
    private String notionPromptsDbId;
    private String notionParentPageId;

    public static IntegrationStatusResponse from(WorkspaceIntegration integration) {
        return IntegrationStatusResponse.builder()
                .type(integration.getType())
                .active(integration.isActive())
                .configured(integration.getAccessTokenEncrypted() != null)
                .connectedAt(integration.getConnectedAt())
                .lastSyncAt(integration.getLastSyncAt())
                .slackTeamId(integration.getSlackTeamId())
                .slackChannelId(integration.getSlackChannelId())
                .notionLogsDbId(integration.getNotionLogsDbId())
                .notionDocsDbId(integration.getNotionDocsDbId())
                .notionPromptsDbId(integration.getNotionPromptsDbId())
                .notionParentPageId(integration.getNotionParentPageId())
                .build();
    }
}
