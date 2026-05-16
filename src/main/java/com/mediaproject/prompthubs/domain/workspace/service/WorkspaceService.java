package com.mediaproject.prompthubs.domain.workspace.service;

import com.mediaproject.prompthubs.domain.account.entity.Account;
import com.mediaproject.prompthubs.domain.account.repository.AccountRepository;
import com.mediaproject.prompthubs.domain.workspace.dto.*;
import com.mediaproject.prompthubs.domain.workspace.entity.Member;
import com.mediaproject.prompthubs.domain.workspace.entity.Workspace;
import com.mediaproject.prompthubs.domain.workspace.repository.MemberRepository;
import com.mediaproject.prompthubs.domain.workspace.repository.WorkspaceRepository;
import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import com.mediaproject.prompthubs.global.security.WorkspaceContext;
import com.mediaproject.prompthubs.global.security.WorkspaceContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final MemberRepository memberRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public WorkspaceResponse create(UUID accountId, CreateWorkspaceRequest request) {
        // Check if slug already exists
        if (workspaceRepository.existsBySlugAndDeletedAtIsNull(request.getSlug())) {
            throw new BusinessException(ErrorCode.WORKSPACE_SLUG_EXISTS);
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        // Create workspace
        Workspace workspace = Workspace.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .build();

        Workspace savedWorkspace = workspaceRepository.save(workspace);

        // Create owner member
        Member ownerMember = Member.builder()
                .account(account)
                .workspace(savedWorkspace)
                .role(Member.Role.OWNER)
                .build();

        memberRepository.save(ownerMember);

        log.info("Workspace created: {} by {}", savedWorkspace.getSlug(), account.getEmail());

        return WorkspaceResponse.from(savedWorkspace, Member.Role.OWNER);
    }

    public List<WorkspaceListResponse> getMyWorkspaces(UUID accountId) {
        List<Member> members = memberRepository.findByAccountIdWithWorkspace(accountId);
        return members.stream()
                .map(WorkspaceListResponse::from)
                .toList();
    }

    public WorkspaceResponse getWorkspace(String slug, UUID accountId) {
        Workspace workspace = workspaceRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));

        Member member = memberRepository.findByAccountIdAndWorkspaceId(accountId, workspace.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_WORKSPACE_MEMBER));

        return WorkspaceResponse.from(workspace, member.getRole());
    }

    @Transactional
    public WorkspaceResponse update(String slug, UpdateWorkspaceRequest request, UUID accountId) {
        Workspace workspace = workspaceRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));

        Member member = memberRepository.findByAccountIdAndWorkspaceId(accountId, workspace.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_WORKSPACE_MEMBER));

        // Only ADMIN or OWNER can update
        if (!member.isAdmin()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION);
        }

        // Check if new slug already exists (if changing slug)
        if (request.getSlug() != null && !request.getSlug().equals(workspace.getSlug())) {
            if (workspaceRepository.existsBySlugAndDeletedAtIsNull(request.getSlug())) {
                throw new BusinessException(ErrorCode.WORKSPACE_SLUG_EXISTS);
            }
        }

        workspace.update(request.getName(), request.getSlug());

        log.info("Workspace updated: {}", workspace.getSlug());

        return WorkspaceResponse.from(workspace, member.getRole());
    }

    @Transactional
    public void delete(String slug, UUID accountId) {
        Workspace workspace = workspaceRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));

        Member member = memberRepository.findByAccountIdAndWorkspaceId(accountId, workspace.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_WORKSPACE_MEMBER));

        // Only OWNER can delete
        if (!member.isOwner()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION, "Only the owner can delete the workspace");
        }

        workspace.softDelete();

        log.info("Workspace deleted: {}", slug);
    }

    // Helper method to get current workspace from context
    public WorkspaceContext getCurrentWorkspaceContext() {
        WorkspaceContext context = WorkspaceContextHolder.getContext();
        if (context == null) {
            throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND, "Workspace context not found");
        }
        return context;
    }
}