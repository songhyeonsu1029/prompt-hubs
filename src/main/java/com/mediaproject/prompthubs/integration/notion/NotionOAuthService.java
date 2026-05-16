package com.mediaproject.prompthubs.integration.notion;

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
import com.mediaproject.prompthubs.integration.notion.dto.NotionPageOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotionOAuthService {

    private final NotionProperties properties;
    private final NotionApiClient apiClient;
    private final NotionDatabaseSetup databaseSetup;
    private final NotionOutboundSyncService outboundSyncService;
    private final WorkspaceIntegrationRepository integrationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final TokenCipher tokenCipher;

    public String buildAuthorizationUrl(String stateSlug) {
        ensureEnabled();
        String redirect = URLEncoder.encode(properties.getRedirectUri(), StandardCharsets.UTF_8);
        String state = URLEncoder.encode(stateSlug, StandardCharsets.UTF_8);
        return "https://api.notion.com/v1/oauth/authorize"
                + "?client_id=" + properties.getClientId()
                + "&response_type=code"
                + "&owner=user"
                + "&redirect_uri=" + redirect
                + "&state=" + state;
    }

    @Transactional
    public WorkspaceIntegration completeOAuth(String code, String stateSlug) {
        ensureEnabled();

        Workspace workspace = workspaceRepository.findBySlugAndDeletedAtIsNull(stateSlug)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));

        JsonNode resp = apiClient.exchangeCode(code);
        String accessToken = resp.path("access_token").asText(null);
        String notionWsId = resp.path("workspace_id").asText(null);
        if (accessToken == null) {
            throw new BusinessException(ErrorCode.INTEGRATION_OAUTH_FAILED, "no access_token");
        }

        WorkspaceIntegration integration = integrationRepository
                .findByWorkspaceIdAndType(workspace.getId(), IntegrationType.NOTION)
                .orElseGet(() -> WorkspaceIntegration.builder()
                        .workspace(workspace)
                        .type(IntegrationType.NOTION)
                        .build());

        integration.updateTokens(tokenCipher.encrypt(accessToken), null);
        integration.updateNotionMeta(notionWsId);
        integration.markConnected();

        WorkspaceIntegration saved = integrationRepository.save(integration);

        // Best-effort DB bootstrap. If parent page is not set, leave dbs null and let
        // /setup endpoint handle it later.
        if (properties.getParentPageId() != null && !properties.getParentPageId().isBlank()
                && saved.getNotionLogsDbId() == null) {
            try {
                databaseSetup.setup(saved);
                outboundSyncService.backfillAll(workspace.getId());
            } catch (Exception e) {
                log.warn("Notion DB bootstrap failed: {}", e.getMessage());
            }
        }

        log.info("Notion integration connected for workspace {}", workspace.getSlug());
        return saved;
    }

    @Transactional
    public WorkspaceIntegration manualSetup() {
        WorkspaceContext context = requireWorkspace();
        WorkspaceIntegration integration = integrationRepository
                .findByWorkspaceIdAndType(context.getWorkspaceId(), IntegrationType.NOTION)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTEGRATION_NOT_FOUND));
        databaseSetup.setup(integration);
        try {
            outboundSyncService.backfillAll(context.getWorkspaceId());
        } catch (Exception e) {
            log.warn("Notion backfill failed (non-fatal): {}", e.getMessage());
        }
        return integration;
    }

    public List<NotionPageOption> searchParentPages(String query) {
        WorkspaceContext context = requireWorkspace();
        WorkspaceIntegration integration = integrationRepository
                .findByWorkspaceIdAndType(context.getWorkspaceId(), IntegrationType.NOTION)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTEGRATION_NOT_FOUND));
        if (integration.getAccessTokenEncrypted() == null) {
            throw new BusinessException(ErrorCode.INTEGRATION_NOT_FOUND, "Notion is not connected yet");
        }
        String token = tokenCipher.decrypt(integration.getAccessTokenEncrypted());
        JsonNode resp = apiClient.searchPages(token, query);

        List<NotionPageOption> options = new ArrayList<>();
        JsonNode results = resp.path("results");
        if (results.isArray()) {
            for (JsonNode page : results) {
                options.add(toOption(page));
            }
        }
        return options;
    }

    @Transactional
    public WorkspaceIntegration selectParentPage(String pageId) {
        if (pageId == null || pageId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "pageId is required");
        }
        WorkspaceContext context = requireWorkspace();
        WorkspaceIntegration integration = integrationRepository
                .findByWorkspaceIdAndType(context.getWorkspaceId(), IntegrationType.NOTION)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTEGRATION_NOT_FOUND));
        if (integration.getAccessTokenEncrypted() == null) {
            throw new BusinessException(ErrorCode.INTEGRATION_NOT_FOUND, "Notion is not connected yet");
        }
        integration.updateNotionParentPage(normalizePageId(pageId));
        try {
            databaseSetup.setup(integration);
        } catch (Exception e) {
            log.warn("Notion DB bootstrap failed after parent page select: {}", e.getMessage());
            throw e;
        }
        try {
            int pushed = outboundSyncService.backfillAll(context.getWorkspaceId());
            log.info("Notion backfill kicked off after parent page select: {} items pushed", pushed);
        } catch (Exception e) {
            log.warn("Notion backfill failed (non-fatal): {}", e.getMessage());
        }
        return integration;
    }

    private NotionPageOption toOption(JsonNode page) {
        String id = page.path("id").asText("");
        String title = extractTitle(page);
        String icon = extractIcon(page);
        String url = page.path("url").asText(null);
        boolean archived = page.path("archived").asBoolean(false);
        return NotionPageOption.builder()
                .id(id)
                .title(title.isBlank() ? "Untitled" : title)
                .icon(icon)
                .url(url)
                .archived(archived)
                .build();
    }

    private String extractTitle(JsonNode page) {
        JsonNode props = page.path("properties");
        if (props.isObject()) {
            var fields = props.properties();
            for (var entry : fields) {
                JsonNode prop = entry.getValue();
                if ("title".equals(prop.path("type").asText())) {
                    JsonNode arr = prop.path("title");
                    if (arr.isArray()) {
                        StringBuilder sb = new StringBuilder();
                        for (JsonNode seg : arr) {
                            sb.append(seg.path("plain_text").asText(""));
                        }
                        return sb.toString();
                    }
                }
            }
        }
        return "";
    }

    private String extractIcon(JsonNode page) {
        JsonNode icon = page.path("icon");
        if (icon.isMissingNode() || icon.isNull()) return null;
        String type = icon.path("type").asText("");
        if ("emoji".equals(type)) return icon.path("emoji").asText(null);
        if ("external".equals(type)) return icon.path("external").path("url").asText(null);
        if ("file".equals(type)) return icon.path("file").path("url").asText(null);
        return null;
    }

    private String normalizePageId(String pageId) {
        String trimmed = pageId.trim().replace("-", "");
        if (trimmed.length() != 32) {
            return pageId.trim();
        }
        return trimmed.substring(0, 8) + "-"
                + trimmed.substring(8, 12) + "-"
                + trimmed.substring(12, 16) + "-"
                + trimmed.substring(16, 20) + "-"
                + trimmed.substring(20, 32);
    }

    public int backfill() {
        WorkspaceContext context = requireWorkspace();
        return outboundSyncService.backfillAll(context.getWorkspaceId());
    }

    @Transactional
    public void disconnect() {
        WorkspaceContext context = requireWorkspace();
        integrationRepository.findByWorkspaceIdAndType(context.getWorkspaceId(), IntegrationType.NOTION)
                .ifPresent(integration -> {
                    integration.deactivate();
                    integration.resetNotion();
                });
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.INTEGRATION_DISABLED, "Notion integration is disabled");
        }
        if (properties.getClientId() == null || properties.getClientId().isBlank()) {
            throw new BusinessException(ErrorCode.INTEGRATION_DISABLED, "Notion client_id not configured");
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
