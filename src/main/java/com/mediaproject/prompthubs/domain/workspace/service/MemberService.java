package com.mediaproject.prompthubs.domain.workspace.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private static final String INVITE_TOKEN_PREFIX = "invite_token:";
    private static final long INVITE_EXPIRATION_HOURS = 24;

    private final MemberRepository memberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AccountRepository accountRepository;
    private final PlanLimitService planLimitService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public InviteResponse createInvite(String slug, InviteRequest request, UUID accountId) {
        Workspace workspace = workspaceRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));

        Member member = memberRepository.findByAccountIdAndWorkspaceId(accountId, workspace.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_WORKSPACE_MEMBER));

        // Only ADMIN+ can invite
        if (!member.isAdmin()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION);
        }

        // Check plan limit for members
        planLimitService.checkMemberLimit(workspace.getId());

        // Cannot invite as OWNER
        if (request.getRole() == Member.Role.OWNER) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Cannot invite as OWNER");
        }

        // Generate invite token
        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(INVITE_EXPIRATION_HOURS);

        // Store invite data in Redis
        InviteData inviteData = new InviteData(workspace.getId(), request.getRole());
        try {
            String key = INVITE_TOKEN_PREFIX + token;
            redisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(inviteData),
                    INVITE_EXPIRATION_HOURS,
                    TimeUnit.HOURS
            );
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to create invite");
        }

        log.info("Invite created for workspace: {} by {}", slug, accountId);

        return InviteResponse.of(token, slug, expiresAt);
    }

    @Transactional
    public MemberResponse joinWorkspace(String slug, String token, UUID accountId) {
        // Get invite data from Redis
        String key = INVITE_TOKEN_PREFIX + token;
        String inviteDataJson = redisTemplate.opsForValue().get(key);

        if (inviteDataJson == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "Invalid or expired invite token");
        }

        InviteData inviteData;
        try {
            inviteData = objectMapper.readValue(inviteDataJson, InviteData.class);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to process invite");
        }

        Workspace workspace = workspaceRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));

        // Verify workspace matches
        if (!workspace.getId().equals(inviteData.workspaceId())) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "Invite token does not match this workspace");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        // Check if already a member
        if (memberRepository.existsByAccountIdAndWorkspaceId(accountId, workspace.getId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Already a member of this workspace");
        }

        // Create member
        Member newMember = Member.builder()
                .account(account)
                .workspace(workspace)
                .role(inviteData.role())
                .build();

        Member savedMember = memberRepository.save(newMember);

        // Delete invite token
        redisTemplate.delete(key);

        log.info("User {} joined workspace {} as {}", account.getEmail(), slug, inviteData.role());

        return MemberResponse.from(savedMember);
    }

    public List<MemberResponse> getMembers(UUID workspaceId) {
        List<Member> members = memberRepository.findByWorkspaceIdWithAccount(workspaceId);
        return members.stream()
                .map(MemberResponse::from)
                .toList();
    }

    @Transactional
    public MemberResponse updateMemberRole(UUID memberId, UpdateMemberRoleRequest request, UUID currentAccountId) {
        WorkspaceContext context = WorkspaceContextHolder.getContext();
        if (context == null) {
            throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND);
        }

        // Only ADMIN+ can change roles
        if (!context.isAdmin()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION);
        }

        Member targetMember = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Member not found"));

        // Verify member belongs to current workspace
        if (!targetMember.getWorkspace().getId().equals(context.getWorkspaceId())) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Member not found in this workspace");
        }

        // Cannot change OWNER's role
        if (targetMember.isOwner()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION, "Cannot change owner's role");
        }

        // Cannot promote to OWNER
        if (request.getRole() == Member.Role.OWNER) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Cannot promote to OWNER");
        }

        // ADMIN cannot change other ADMIN's role (only OWNER can)
        if (targetMember.getRole() == Member.Role.ADMIN && !context.isOwner()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION, "Only owner can change admin's role");
        }

        targetMember.changeRole(request.getRole());

        log.info("Member {} role changed to {} in workspace {}",
                memberId, request.getRole(), context.getWorkspaceSlug());

        return MemberResponse.from(targetMember);
    }

    @Transactional
    public void removeMember(UUID memberId, UUID currentAccountId) {
        WorkspaceContext context = WorkspaceContextHolder.getContext();
        if (context == null) {
            throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND);
        }

        // Only ADMIN+ can remove members
        if (!context.isAdmin()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION);
        }

        Member targetMember = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Member not found"));

        // Verify member belongs to current workspace
        if (!targetMember.getWorkspace().getId().equals(context.getWorkspaceId())) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Member not found in this workspace");
        }

        // Cannot remove OWNER
        if (targetMember.isOwner()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION, "Cannot remove owner");
        }

        // ADMIN cannot remove other ADMIN (only OWNER can)
        if (targetMember.getRole() == Member.Role.ADMIN && !context.isOwner()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION, "Only owner can remove admin");
        }

        memberRepository.delete(targetMember);

        log.info("Member {} removed from workspace {}", memberId, context.getWorkspaceSlug());
    }

    // Inner record for invite data
    private record InviteData(UUID workspaceId, Member.Role role) {}
}