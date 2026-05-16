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
import com.mediaproject.prompthubs.integration.mcp.dto.McpSearchRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Endpoints exposed to MCP clients (Claude Code, etc.). Authenticated via X-API-Key.
 * Reuses existing domain services — no new business logic here.
 */
@RestController
@RequestMapping("/api/v1/external/mcp")
@RequiredArgsConstructor
public class McpExternalController {

    private final LogService logService;
    private final DocService docService;
    private final PromptService promptService;
    private final MemberRepository memberRepository;

    @PostMapping("/logs")
    public ResponseEntity<LogResponse> saveLog(@Valid @RequestBody CreateLogRequest request) {
        UUID authorId = resolveOwnerAccountId();
        LogResponse created = logService.create(request, authorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/search")
    public ResponseEntity<PageResponse<PromptListResponse>> searchPrompts(@RequestBody(required = false) McpSearchRequest req) {
        McpSearchRequest body = req == null ? new McpSearchRequest() : req;
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "updatedAt"));
        PageResponse<PromptListResponse> page = promptService.getPrompts(
                Prompt.Status.APPROVED,
                body.getCategory(),
                body.getKeyword(),
                pageable
        );
        return ResponseEntity.ok(page);
    }

    @GetMapping("/prompts/{id}")
    public ResponseEntity<PromptResponse> getPrompt(@PathVariable UUID id) {
        return ResponseEntity.ok(promptService.getPrompt(id));
    }

    @GetMapping("/docs")
    public ResponseEntity<PageResponse<DocResponse>> listDocs(@RequestParam(required = false) String category) {
        Pageable pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return ResponseEntity.ok(docService.getDocs(category, pageable));
    }

    @GetMapping("/docs/{id}")
    public ResponseEntity<DocResponse> getDoc(@PathVariable UUID id) {
        return ResponseEntity.ok(docService.getDoc(id));
    }

    private UUID resolveOwnerAccountId() {
        WorkspaceContext context = WorkspaceContextHolder.getContext();
        if (context == null) {
            throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND, "Workspace context not found");
        }
        Member owner = memberRepository.findOwnerByWorkspaceId(context.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND,
                        "Workspace owner not found"));
        return owner.getAccount().getId();
    }
}
