package com.mediaproject.prompthubs.domain.workspace.controller;

import com.mediaproject.prompthubs.domain.workspace.dto.*;
import com.mediaproject.prompthubs.domain.workspace.service.MemberService;
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
import java.util.UUID;

@Tag(name = "Member", description = "Workspace member management API")
@RestController
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @Operation(summary = "Create invite", description = "Generate invite token (ADMIN+)")
    @PostMapping("/api/v1/workspaces/{slug}/invite")
    public ResponseEntity<InviteResponse> createInvite(
            @PathVariable String slug,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody InviteRequest request) {
        InviteResponse response = memberService.createInvite(slug, request, userDetails.getAccountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Join workspace", description = "Accept invite and join workspace")
    @PostMapping("/api/v1/workspaces/{slug}/join")
    public ResponseEntity<MemberResponse> joinWorkspace(
            @PathVariable String slug,
            @RequestParam String token,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        MemberResponse response = memberService.joinWorkspace(slug, token, userDetails.getAccountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get members", description = "Get list of workspace members")
    @GetMapping("/api/v1/w/{slug}/members")
    public ResponseEntity<List<MemberResponse>> getMembers(@PathVariable String slug) {
        WorkspaceContext context = WorkspaceContextHolder.getContext();
        List<MemberResponse> response = memberService.getMembers(context.getWorkspaceId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update member role", description = "Change member's role (ADMIN+)")
    @PatchMapping("/api/v1/w/{slug}/members/{memberId}")
    public ResponseEntity<MemberResponse> updateMemberRole(
            @PathVariable String slug,
            @PathVariable UUID memberId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        MemberResponse response = memberService.updateMemberRole(memberId, request, userDetails.getAccountId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Remove member", description = "Remove member from workspace (ADMIN+)")
    @DeleteMapping("/api/v1/w/{slug}/members/{memberId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable String slug,
            @PathVariable UUID memberId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        memberService.removeMember(memberId, userDetails.getAccountId());
        return ResponseEntity.noContent().build();
    }
}