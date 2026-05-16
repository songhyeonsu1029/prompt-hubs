package com.mediaproject.prompthubs.domain.log.controller;

import com.mediaproject.prompthubs.domain.log.dto.*;
import com.mediaproject.prompthubs.domain.log.entity.PromptLog;
import com.mediaproject.prompthubs.domain.log.service.LogService;
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

import java.util.UUID;

@Tag(name = "Log", description = "Prompt experiment log API")
@RestController
@RequestMapping("/api/v1/w/{slug}/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    @Operation(summary = "Create log", description = "Create a new prompt experiment log")
    @PostMapping
    public ResponseEntity<LogResponse> create(
            @PathVariable String slug,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateLogRequest request) {
        LogResponse response = logService.create(request, userDetails.getAccountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get logs", description = "Get list of logs with filters and pagination")
    @GetMapping
    public ResponseEntity<PageResponse<LogResponse>> getLogs(
            @PathVariable String slug,
            @RequestParam(required = false) PromptLog.Status status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID authorId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<LogResponse> response = logService.getLogs(status, keyword, authorId, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get log", description = "Get a specific log by ID")
    @GetMapping("/{logId}")
    public ResponseEntity<LogResponse> getLog(
            @PathVariable String slug,
            @PathVariable UUID logId) {
        LogResponse response = logService.getLog(logId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update log", description = "Update an existing log")
    @PatchMapping("/{logId}")
    public ResponseEntity<LogResponse> update(
            @PathVariable String slug,
            @PathVariable UUID logId,
            @Valid @RequestBody UpdateLogRequest request) {
        LogResponse response = logService.update(logId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete log", description = "Delete a log")
    @DeleteMapping("/{logId}")
    public ResponseEntity<Void> delete(
            @PathVariable String slug,
            @PathVariable UUID logId) {
        logService.delete(logId);
        return ResponseEntity.noContent().build();
    }
}