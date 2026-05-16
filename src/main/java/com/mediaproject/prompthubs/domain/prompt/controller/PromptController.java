package com.mediaproject.prompthubs.domain.prompt.controller;

import com.mediaproject.prompthubs.domain.prompt.dto.*;
import com.mediaproject.prompthubs.domain.prompt.entity.Prompt;
import com.mediaproject.prompthubs.domain.prompt.service.PromptService;
import com.mediaproject.prompthubs.global.common.PageResponse;
import com.mediaproject.prompthubs.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Prompt", description = "Verified prompt library API")
@RestController
@RequestMapping("/api/v1/w/{slug}")
@RequiredArgsConstructor
public class PromptController {

    private final PromptService promptService;

    @Operation(summary = "Create prompt", description = "Create a new prompt with Pre-prompting fields")
    @PostMapping("/prompts")
    public ResponseEntity<PromptResponse> create(
            @PathVariable String slug,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreatePromptRequest request) {
        PromptResponse response = promptService.create(request, userDetails.getAccountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get prompts", description = "Get list of prompts with filters and pagination")
    @GetMapping("/prompts")
    public ResponseEntity<PageResponse<PromptListResponse>> getPrompts(
            @PathVariable String slug,
            @RequestParam(required = false) Prompt.Status status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<PromptListResponse> response = promptService.getPrompts(status, category, keyword, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get prompt", description = "Get a specific prompt with current version info")
    @GetMapping("/prompts/{promptId}")
    public ResponseEntity<PromptResponse> getPrompt(
            @PathVariable String slug,
            @PathVariable UUID promptId) {
        PromptResponse response = promptService.getPrompt(promptId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update linked docs", description = "Update the documents linked to a prompt")
    @PutMapping("/prompts/{promptId}/docs")
    public ResponseEntity<PromptResponse> updateLinkedDocs(
            @PathVariable String slug,
            @PathVariable UUID promptId,
            @RequestBody List<UUID> docIds) {
        PromptResponse response = promptService.updateLinkedDocs(promptId, docIds);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Promote log to prompt", description = "Create a prompt from an experiment log")
    @PostMapping("/logs/{logId}/promote")
    public ResponseEntity<PromptResponse> promoteLog(
            @PathVariable String slug,
            @PathVariable UUID logId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PromoteLogRequest request) {
        PromptResponse response = promptService.promoteLog(logId, request, userDetails.getAccountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}