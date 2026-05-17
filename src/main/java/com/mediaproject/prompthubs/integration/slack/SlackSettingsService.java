package com.mediaproject.prompthubs.integration.slack;

import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import com.mediaproject.prompthubs.global.security.WorkspaceContext;
import com.mediaproject.prompthubs.global.security.WorkspaceContextHolder;
import com.mediaproject.prompthubs.integration.common.crypto.TokenCipher;
import com.mediaproject.prompthubs.integration.common.entity.IntegrationType;
import com.mediaproject.prompthubs.integration.common.entity.WorkspaceIntegration;
import com.mediaproject.prompthubs.integration.common.repository.WorkspaceIntegrationRepository;
import com.mediaproject.prompthubs.integration.slack.dto.SlackChannelResponse;
import com.mediaproject.prompthubs.integration.slack.dto.SlackSettingsRequest;
import com.slack.api.model.Conversation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Slack 채널 선택 및 알림 종류 토글 설정. OAuth 연결 이후 동작한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlackSettingsService {

    private final WorkspaceIntegrationRepository integrationRepository;
    private final SlackApiClient apiClient;
    private final TokenCipher tokenCipher;

    @Transactional(readOnly = true)
    public List<SlackChannelResponse> listChannels() {
        WorkspaceIntegration integration = requireConnectedIntegration();
        String botToken = tokenCipher.decrypt(integration.getAccessTokenEncrypted());
        List<Conversation> channels = apiClient.listBotChannels(botToken);
        return channels.stream()
                .map(c -> SlackChannelResponse.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .privateChannel(Boolean.TRUE.equals(c.isPrivate()))
                        .build())
                .toList();
    }

    @Transactional
    public void updateSettings(SlackSettingsRequest request) {
        WorkspaceIntegration integration = requireConnectedIntegration();
        integration.updateSlackChannel(request.getChannelId(), request.getChannelName());
        integration.updateSlackNotificationFlags(
                request.isNotifyReviewRequested(),
                request.isNotifyReviewCompleted(),
                request.isNotifyVersionCreated(),
                request.isNotifyPlanWarning(),
                request.isNotifyPrePrompting());
        log.info("Slack settings updated for workspace {} (channel={})",
                integration.getWorkspace().getSlug(), request.getChannelId());
    }

    private WorkspaceIntegration requireConnectedIntegration() {
        WorkspaceContext context = WorkspaceContextHolder.getContext();
        if (context == null) {
            throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND);
        }
        WorkspaceIntegration integration = integrationRepository
                .findByWorkspaceIdAndType(context.getWorkspaceId(), IntegrationType.SLACK)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTEGRATION_NOT_FOUND,
                        "Slack is not connected"));
        if (!integration.isActive() || integration.getAccessTokenEncrypted() == null) {
            throw new BusinessException(ErrorCode.INTEGRATION_NOT_FOUND, "Slack is not connected");
        }
        return integration;
    }
}
