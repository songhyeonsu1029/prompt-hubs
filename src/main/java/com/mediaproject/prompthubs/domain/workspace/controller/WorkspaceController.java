package com.mediaproject.prompthubs.domain.workspace.controller;

import com.mediaproject.prompthubs.domain.workspace.dto.*;
import com.mediaproject.prompthubs.domain.workspace.service.PlanLimitService;
import com.mediaproject.prompthubs.domain.workspace.service.WorkspaceService;
import com.mediaproject.prompthubs.global.security.CustomUserDetails;
import com.mediaproject.prompthubs.global.security.WorkspaceContext;
import com.mediaproject.prompthubs.global.security.WorkspaceContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Workspace", description = "Workspace management API")
@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final PlanLimitService planLimitService;

    @Operation(summary = "Create workspace", description = "Create a new workspace (creator becomes OWNER)")
    @PostMapping
    public ResponseEntity<WorkspaceResponse> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateWorkspaceRequest request) {
        WorkspaceResponse response = workspaceService.create(userDetails.getAccountId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get my workspaces", description = "Get list of workspaces I belong to")
    @GetMapping
    public ResponseEntity<List<WorkspaceListResponse>> getMyWorkspaces(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<WorkspaceListResponse> response = workspaceService.getMyWorkspaces(userDetails.getAccountId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get workspace", description = "Get workspace details by slug")
    @GetMapping("/{slug}")
    public ResponseEntity<WorkspaceResponse> getWorkspace(
            @PathVariable String slug,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        WorkspaceResponse response = workspaceService.getWorkspace(slug, userDetails.getAccountId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update workspace", description = "Update workspace settings (ADMIN+)")
    @PatchMapping("/{slug}")
    public ResponseEntity<WorkspaceResponse> update(
            @PathVariable String slug,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateWorkspaceRequest request) {
        WorkspaceResponse response = workspaceService.update(slug, request, userDetails.getAccountId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete workspace", description = "Soft delete workspace (OWNER only)")
    @DeleteMapping("/{slug}")
    public ResponseEntity<Void> delete(
            @PathVariable String slug,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        workspaceService.delete(slug, userDetails.getAccountId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get workspace usage", description = "Get current plan usage and limits")
    @GetMapping("/{slug}/usage")
    public ResponseEntity<UsageResponse> getUsage(@PathVariable String slug) {
        WorkspaceContext context = WorkspaceContextHolder.getContext();
        UsageResponse response = planLimitService.getUsage(context.getWorkspaceId());
        return ResponseEntity.ok(response);
    }
}