package com.mediaproject.prompthubs.domain.prompt.controller;

import com.mediaproject.prompthubs.domain.prompt.dto.*;
import com.mediaproject.prompthubs.domain.prompt.service.VersionService;
import com.mediaproject.prompthubs.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Version", description = "Prompt version control API")
@RestController
@RequestMapping("/api/v1/w/{slug}/prompts/{promptId}")
@RequiredArgsConstructor
public class VersionController {

    private final VersionService versionService;

    @Operation(summary = "Create new version", description = "Create a new version of the prompt")
    @PostMapping("/versions")
    public ResponseEntity<VersionResponse> createVersion(
            @PathVariable String slug,
            @PathVariable UUID promptId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateVersionRequest request) {
        VersionResponse response = versionService.createVersion(promptId, request, userDetails.getAccountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get version history", description = "Get all versions of a prompt in descending order")
    @GetMapping("/versions")
    public ResponseEntity<List<VersionResponse>> getVersionHistory(
            @PathVariable String slug,
            @PathVariable UUID promptId) {
        List<VersionResponse> response = versionService.getVersionHistory(promptId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get version diff", description = "Compare two versions and get line-by-line diff")
    @GetMapping("/versions/{versionId}/diff")
    public ResponseEntity<DiffResponse> getDiff(
            @PathVariable String slug,
            @PathVariable UUID promptId,
            @PathVariable UUID versionId,
            @RequestParam UUID compareWith) {
        DiffResponse response = versionService.getDiff(promptId, versionId, compareWith);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Rollback to version", description = "Create a new version with content from a previous version")
    @PostMapping("/rollback")
    public ResponseEntity<VersionResponse> rollback(
            @PathVariable String slug,
            @PathVariable UUID promptId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody RollbackRequest request) {
        VersionResponse response = versionService.rollback(promptId, request, userDetails.getAccountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

}
