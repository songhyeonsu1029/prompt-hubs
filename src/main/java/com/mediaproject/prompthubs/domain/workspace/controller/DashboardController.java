package com.mediaproject.prompthubs.domain.workspace.controller;

import com.mediaproject.prompthubs.domain.workspace.dto.DashboardResponse;
import com.mediaproject.prompthubs.domain.workspace.service.DashboardService;
import com.mediaproject.prompthubs.global.security.CustomUserDetails;
import com.mediaproject.prompthubs.global.security.WorkspaceContext;
import com.mediaproject.prompthubs.global.security.WorkspaceContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "Workspace dashboard API")
@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "Get dashboard", description = "Get workspace dashboard with stats and recent activity")
    @GetMapping("/api/v1/w/{slug}/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard(
            @PathVariable String slug,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        WorkspaceContext context = WorkspaceContextHolder.getContext();
        DashboardResponse response = dashboardService.getDashboard(
                context.getWorkspaceId(), userDetails.getAccountId());
        return ResponseEntity.ok(response);
    }
}