package com.mediaproject.prompthubs.integration.slack;

import com.mediaproject.prompthubs.domain.workspace.entity.Workspace;
import com.mediaproject.prompthubs.domain.workspace.repository.WorkspaceRepository;
import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import com.mediaproject.prompthubs.global.security.WorkspaceContext;
import com.mediaproject.prompthubs.global.security.WorkspaceContextHolder;
import com.mediaproject.prompthubs.integration.common.crypto.TokenCipher;
import com.mediaproject.prompthubs.integration.common.entity.IntegrationType;
import com.mediaproject.prompthubs.integration.common.entity.WorkspaceIntegration;
import com.mediaproject.prompthubs.integration.common.repository.WorkspaceIntegrationRepository;
import com.slack.api.methods.response.oauth.OAuthV2AccessResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackOAuthService {

    private final SlackProperties properties;
    private final SlackApiClient apiClient;
    private final WorkspaceIntegrationRepository integrationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final TokenCipher tokenCipher;

    public String buildAuthorizationUrl(String stateSlug) {
        ensureEnabled();
        String scope = URLEncoder.encode(properties.getBotScope(), StandardCharsets.UTF_8);
        String redirect = URLEncoder.encode(properties.getRedirectUri(), StandardCharsets.UTF_8);
        String state = URLEncoder.encode(stateSlug, StandardCharsets.UTF_8);
        return "https://slack.com/oauth/v2/authorize"
                + "?client_id=" + properties.getClientId()
                + "&scope=" + scope
                + "&redirect_uri=" + redirect
                + "&state=" + state;
    }

    @Transactional
    public WorkspaceIntegration completeOAuth(String code, String stateSlug) {
        ensureEnabled();

        Workspace workspace = workspaceRepository.findBySlugAndDeletedAtIsNull(stateSlug)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));

        OAuthV2AccessResponse resp = apiClient.exchangeCode(code);
        if (resp == null || !resp.isOk()) {
            throw new BusinessException(ErrorCode.INTEGRATION_OAUTH_FAILED,
                    resp == null ? "no response" : resp.getError());
        }

        WorkspaceIntegration integration = integrationRepository
                .findByWorkspaceIdAndType(workspace.getId(), IntegrationType.SLACK)
                .orElseGet(() -> WorkspaceIntegration.builder()
                        .workspace(workspace)
                        .type(IntegrationType.SLACK)
                        .build());

        String botToken = resp.getAccessToken();
        integration.updateTokens(tokenCipher.encrypt(botToken), null);
        integration.updateSlackMeta(resp.getTeam() != null ? resp.getTeam().getId() : null,
                resp.getIncomingWebhook() != null ? resp.getIncomingWebhook().getChannelId() : null,
                resp.getBotUserId());
        integration.markConnected();

        WorkspaceIntegration saved = integrationRepository.save(integration);
        log.info("Slack integration connected for workspace {}", workspace.getSlug());
        return saved;
    }

    @Transactional
    public void disconnect() {
        WorkspaceContext context = requireWorkspace();
        integrationRepository.findByWorkspaceIdAndType(context.getWorkspaceId(), IntegrationType.SLACK)
                .ifPresent(integration -> {
                    integration.deactivate();
                    log.info("Slack integration disconnected for workspace {}", context.getWorkspaceSlug());
                });
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.INTEGRATION_DISABLED, "Slack integration is disabled");
        }
        if (properties.getClientId() == null || properties.getClientId().isBlank()) {
            throw new BusinessException(ErrorCode.INTEGRATION_DISABLED, "Slack client_id not configured");
        }
    }

    private WorkspaceContext requireWorkspace() {
        WorkspaceContext context = WorkspaceContextHolder.getContext();
        if (context == null) {
            throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND);
        }
        return context;
    }
}
