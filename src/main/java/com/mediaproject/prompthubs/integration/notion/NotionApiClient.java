package com.mediaproject.prompthubs.integration.notion;

import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.Map;

/**
 * Lightweight wrapper over the Notion REST API using Spring WebClient.
 * Returns parsed JsonNode trees rather than fixed DTOs because Notion's
 * page/property model is highly polymorphic.
 */
@Slf4j
@Component
public class NotionApiClient {

    private final WebClient webClient;
    private final NotionProperties properties;
    private final ObjectMapper objectMapper;

    public NotionApiClient(NotionProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(properties.getApiBase() == null ? "https://api.notion.com/v1" : properties.getApiBase())
                .defaultHeader("Notion-Version", properties.getApiVersion() == null ? "2022-06-28" : properties.getApiVersion())
                .build();
    }

    public JsonNode exchangeCode(String code) {
        if (properties.getClientId() == null || properties.getClientSecret() == null) {
            throw new BusinessException(ErrorCode.INTEGRATION_DISABLED, "Notion client credentials not configured");
        }
        String basic = Base64.getEncoder().encodeToString(
                (properties.getClientId() + ":" + properties.getClientSecret()).getBytes());
        Map<String, String> body = Map.of(
                "grant_type", "authorization_code",
                "code", code,
                "redirect_uri", properties.getRedirectUri()
        );
        return post("/oauth/token", "Basic " + basic, body);
    }

    public JsonNode createDatabase(String botToken, JsonNode requestBody) {
        return post("/databases", "Bearer " + botToken, requestBody);
    }

    public JsonNode createPage(String botToken, JsonNode requestBody) {
        return post("/pages", "Bearer " + botToken, requestBody);
    }

    public JsonNode updatePage(String botToken, String pageId, JsonNode requestBody) {
        return patch("/pages/" + pageId, "Bearer " + botToken, requestBody);
    }

    public JsonNode queryDatabase(String botToken, String databaseId, JsonNode filter) {
        return post("/databases/" + databaseId + "/query", "Bearer " + botToken,
                filter == null ? objectMapper.createObjectNode() : filter);
    }

    public JsonNode getPageBlocks(String botToken, String pageId) {
        return get("/blocks/" + pageId + "/children", "Bearer " + botToken);
    }

    public JsonNode searchPages(String botToken, String query) {
        var body = objectMapper.createObjectNode();
        if (query != null && !query.isBlank()) {
            body.put("query", query);
        }
        var filter = body.putObject("filter");
        filter.put("value", "page");
        filter.put("property", "object");
        var sort = body.putObject("sort");
        sort.put("direction", "descending");
        sort.put("timestamp", "last_edited_time");
        body.put("page_size", 25);
        return post("/search", "Bearer " + botToken, body);
    }

    private JsonNode post(String path, String authHeader, Object body) {
        try {
            return webClient.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw translate("POST", path, e);
        }
    }

    private JsonNode patch(String path, String authHeader, Object body) {
        try {
            return webClient.patch()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw translate("PATCH", path, e);
        }
    }

    private JsonNode get(String path, String authHeader) {
        try {
            return webClient.get()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw translate("GET", path, e);
        }
    }

    private BusinessException translate(String method, String path, WebClientResponseException e) {
        log.error("Notion API {} {} failed: {}", method, path, e.getResponseBodyAsString());
        int status = e.getStatusCode().value();
        if (status == 401) {
            // Token revoked, expired, or integration removed in Notion. The caller should
            // deactivate the integration; retrying with the same token will fail again.
            return new BusinessException(ErrorCode.INTEGRATION_OAUTH_FAILED, e.getMessage());
        }
        return new BusinessException(ErrorCode.EXTERNAL_API_ERROR, e.getMessage());
    }
}
