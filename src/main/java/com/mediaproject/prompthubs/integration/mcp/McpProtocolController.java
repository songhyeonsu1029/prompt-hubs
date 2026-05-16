package com.mediaproject.prompthubs.integration.mcp;

import com.mediaproject.prompthubs.domain.doc.dto.DocResponse;
import com.mediaproject.prompthubs.domain.doc.service.DocService;
import com.mediaproject.prompthubs.domain.log.dto.CreateLogRequest;
import com.mediaproject.prompthubs.domain.log.dto.LogResponse;
import com.mediaproject.prompthubs.domain.log.service.LogService;
import com.mediaproject.prompthubs.domain.prompt.dto.PromptListResponse;
import com.mediaproject.prompthubs.domain.prompt.dto.PromptResponse;
import com.mediaproject.prompthubs.domain.prompt.entity.Prompt;
import com.mediaproject.prompthubs.domain.prompt.service.PromptService;
import com.mediaproject.prompthubs.domain.workspace.entity.Member;
import com.mediaproject.prompthubs.domain.workspace.repository.MemberRepository;
import com.mediaproject.prompthubs.global.common.PageResponse;
import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import com.mediaproject.prompthubs.global.security.WorkspaceContext;
import com.mediaproject.prompthubs.global.security.WorkspaceContextHolder;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

/**
 * MCP Streamable HTTP transport — POST /mcp.
 *
 * Speaks JSON-RPC 2.0 with the following methods:
 *   • initialize                      — handshake, returns server capabilities
 *   • notifications/initialized       — client side ack, no response
 *   • tools/list                      — returns the {@link McpToolCatalog} array
 *   • tools/call                      — dispatches to one of the four domain handlers
 *   • ping                            — empty result
 *
 * Authentication: handled in {@link McpAuthFilter} via {@code Authorization: Bearer ph_…}
 * (also accepts the legacy {@code X-API-Key} header). The filter injects a
 * {@link WorkspaceContext} keyed to the API key's workspace before we run.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class McpProtocolController {

    private static final String PROTOCOL_VERSION = "2024-11-05";
    private static final String SERVER_NAME = "prompthubs";
    private static final String SERVER_VERSION = "0.2.0";

    private final ObjectMapper mapper;
    private final McpToolCatalog catalog;
    private final LogService logService;
    private final DocService docService;
    private final PromptService promptService;
    private final MemberRepository memberRepository;

    @PostMapping("/mcp")
    public ResponseEntity<JsonNode> handle(@RequestBody JsonNode request, HttpServletResponse http) {
        // Claude Code expects a session id header back from the server. We're stateless,
        // so we just echo a stable per-workspace id rather than tracking sessions.
        WorkspaceContext ctx = WorkspaceContextHolder.getContext();
        if (ctx != null) {
            http.setHeader("Mcp-Session-Id", "ws-" + ctx.getWorkspaceId());
        }

        JsonNode idNode = request.get("id");
        String method = request.path("method").asText("");
        JsonNode params = request.path("params");

        // Notification (no id) — accept and return nothing.
        if (idNode == null || idNode.isNull()) {
            log.debug("MCP notification: {}", method);
            return ResponseEntity.accepted().build();
        }

        try {
            JsonNode result = switch (method) {
                case "initialize" -> initialize();
                case "tools/list" -> toolsList();
                case "tools/call" -> toolsCall(params);
                case "ping" -> mapper.createObjectNode();
                default -> throw new McpRpcException(-32601, "Method not found: " + method);
            };
            return ResponseEntity.ok(success(idNode, result));
        } catch (McpRpcException e) {
            return ResponseEntity.ok(error(idNode, e.code, e.getMessage()));
        } catch (BusinessException e) {
            log.warn("MCP business error: {}", e.getMessage());
            return ResponseEntity.ok(error(idNode, -32000, e.getMessage()));
        } catch (Exception e) {
            log.error("MCP internal error", e);
            return ResponseEntity.ok(error(idNode, -32603, "Internal error: " + e.getMessage()));
        }
    }

    // ---------- Method handlers ----------

    private JsonNode initialize() {
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);
        ObjectNode caps = result.putObject("capabilities");
        caps.putObject("tools");
        ObjectNode info = result.putObject("serverInfo");
        info.put("name", SERVER_NAME);
        info.put("version", SERVER_VERSION);
        result.put("instructions",
                "Prompt Hubs MCP server. Use search_prompts/list_docs BEFORE drafting prompts or code " +
                "to leverage the team's vetted library; call save_log AFTER meaningful exchanges to " +
                "archive them. All operations are scoped to the API key's workspace.");
        return result;
    }

    private JsonNode toolsList() {
        ObjectNode result = mapper.createObjectNode();
        result.set("tools", catalog.toolsList());
        return result;
    }

    private JsonNode toolsCall(JsonNode params) {
        String name = params.path("name").asText("");
        JsonNode args = params.path("arguments");
        if (args == null || args.isMissingNode() || args.isNull()) {
            args = mapper.createObjectNode();
        }
        Object dto = switch (name) {
            case "save_log" -> doSaveLog(args);
            case "search_prompts" -> doSearchPrompts(args);
            case "get_prompt" -> doGetPrompt(args);
            case "list_docs" -> doListDocs(args);
            case "get_doc" -> doGetDoc(args);
            default -> throw new McpRpcException(-32602, "Unknown tool: " + name);
        };
        return wrapTextContent(dto);
    }

    // ---------- Tool implementations ----------

    private LogResponse doSaveLog(JsonNode args) {
        CreateLogRequest req;
        try {
            req = mapper.treeToValue(args, CreateLogRequest.class);
        } catch (Exception e) {
            throw new McpRpcException(-32602, "Invalid arguments for save_log: " + e.getMessage());
        }
        return logService.create(req, resolveOwnerAccountId());
    }

    private PageResponse<PromptListResponse> doSearchPrompts(JsonNode args) {
        String keyword = textOrNull(args, "keyword");
        String category = textOrNull(args, "category");
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return promptService.getPrompts(Prompt.Status.APPROVED, category, keyword, pageable);
    }

    private PromptResponse doGetPrompt(JsonNode args) {
        String idStr = textOrNull(args, "id");
        if (idStr == null) throw new McpRpcException(-32602, "`id` is required for get_prompt");
        UUID id;
        try {
            id = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            throw new McpRpcException(-32602, "`id` is not a valid UUID");
        }
        return promptService.getPrompt(id);
    }

    private PageResponse<DocResponse> doListDocs(JsonNode args) {
        String category = textOrNull(args, "category");
        Pageable pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return docService.getDocs(category, pageable);
    }

    private DocResponse doGetDoc(JsonNode args) {
        String idStr = textOrNull(args, "id");
        if (idStr == null) throw new McpRpcException(-32602, "`id` is required for get_doc");
        UUID id;
        try {
            id = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            throw new McpRpcException(-32602, "`id` is not a valid UUID");
        }
        return docService.getDoc(id);
    }

    // ---------- Helpers ----------

    private UUID resolveOwnerAccountId() {
        WorkspaceContext ctx = WorkspaceContextHolder.getContext();
        if (ctx == null) throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND);
        Member owner = memberRepository.findOwnerByWorkspaceId(ctx.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Workspace owner not found"));
        return owner.getAccount().getId();
    }

    private static String textOrNull(JsonNode args, String field) {
        JsonNode n = args.path(field);
        if (n.isMissingNode() || n.isNull()) return null;
        String s = n.asText("");
        return s.isBlank() ? null : s;
    }

    private ObjectNode wrapTextContent(Object payload) {
        String text;
        try {
            text = payload == null
                    ? ""
                    : mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (Exception e) {
            text = String.valueOf(payload);
        }
        ObjectNode result = mapper.createObjectNode();
        var content = result.putArray("content");
        ObjectNode block = content.addObject();
        block.put("type", "text");
        block.put("text", text);
        return result;
    }

    private ObjectNode success(JsonNode id, JsonNode result) {
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("jsonrpc", "2.0");
        envelope.set("id", id);
        envelope.set("result", result);
        return envelope;
    }

    private ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("jsonrpc", "2.0");
        envelope.set("id", id);
        ObjectNode err = envelope.putObject("error");
        err.put("code", code);
        err.put("message", message);
        return envelope;
    }

    private static class McpRpcException extends RuntimeException {
        final int code;
        McpRpcException(int code, String message) {
            super(message);
            this.code = code;
        }
    }
}
