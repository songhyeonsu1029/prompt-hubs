package com.mediaproject.prompthubs.integration.notion;

import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import com.mediaproject.prompthubs.integration.common.crypto.TokenCipher;
import com.mediaproject.prompthubs.integration.common.entity.WorkspaceIntegration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Bootstraps the three Notion databases (Logs, Docs, Prompts) under the
 * configured parent page on first connect.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotionDatabaseSetup {

    private final NotionApiClient apiClient;
    private final NotionProperties properties;
    private final TokenCipher tokenCipher;
    private final ObjectMapper objectMapper;

    public void setup(WorkspaceIntegration integration) {
        String parentPageId = integration.getNotionParentPageId();
        if (parentPageId == null || parentPageId.isBlank()) {
            parentPageId = properties.getParentPageId();
        }
        if (parentPageId == null || parentPageId.isBlank()) {
            throw new BusinessException(ErrorCode.INTEGRATION_DISABLED,
                    "Notion parent page is not selected for this workspace");
        }
        String token = tokenCipher.decrypt(integration.getAccessTokenEncrypted());

        String logsDb = createDb(token, parentPageId, "📋 Prompt Logs",
                List.of("프롬프트", "상태", "모델", "태그", "결과 요약"));
        String docsDb = createDb(token, parentPageId, "📄 Prompt Docs",
                List.of("제목", "카테고리"));
        String promptsDb = createDb(token, parentPageId, "🎯 Prompts",
                List.of("제목", "상태", "성공 기준", "검증 방법", "카테고리", "태그", "버전"));

        integration.updateNotionDatabases(logsDb, docsDb, promptsDb);
        log.info("Notion databases created for workspace {}: logs={}, docs={}, prompts={}",
                integration.getWorkspace().getSlug(), logsDb, docsDb, promptsDb);
    }

    private String createDb(String token, String parentPageId, String title, List<String> textProperties) {
        ObjectNode body = objectMapper.createObjectNode();

        ObjectNode parent = body.putObject("parent");
        parent.put("type", "page_id");
        parent.put("page_id", parentPageId);

        ArrayNode titleArr = body.putArray("title");
        ObjectNode titleEl = titleArr.addObject();
        titleEl.put("type", "text");
        ObjectNode titleText = titleEl.putObject("text");
        titleText.put("content", title);

        ObjectNode props = body.putObject("properties");
        // Notion requires a title property. The first text property becomes the title.
        boolean first = true;
        for (String name : textProperties) {
            ObjectNode prop = props.putObject(name);
            if (first) {
                prop.putObject("title");
                first = false;
            } else {
                prop.putObject("rich_text");
            }
        }

        JsonNode resp = apiClient.createDatabase(token, body);
        return resp.path("id").asText();
    }
}
