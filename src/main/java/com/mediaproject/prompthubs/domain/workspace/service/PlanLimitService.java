package com.mediaproject.prompthubs.domain.workspace.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.mediaproject.prompthubs.domain.prompt.repository.PromptRepository;
import com.mediaproject.prompthubs.domain.prompt.repository.PromptVersionRepository;
import com.mediaproject.prompthubs.domain.workspace.dto.UsageResponse;
import com.mediaproject.prompthubs.domain.workspace.entity.Workspace;
import com.mediaproject.prompthubs.domain.workspace.repository.MemberRepository;
import com.mediaproject.prompthubs.domain.workspace.repository.WorkspaceRepository;
import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import com.mediaproject.prompthubs.integration.common.event.PlanLimitWarningEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanLimitService {

    private static final String USAGE_CACHE_PREFIX = "usage:";
    private static final long CACHE_TTL_MINUTES = 5;

    // FREE plan limits
    private static final int FREE_MEMBER_LIMIT = 5;
    private static final int FREE_PROMPT_LIMIT = 50;
    private static final int FREE_LOG_RETENTION_DAYS = 30;
    private static final int FREE_VERSION_LIMIT = 10;

    // PRO/ENTERPRISE limits (unlimited = -1)
    private static final int UNLIMITED = -1;

    private final WorkspaceRepository workspaceRepository;
    private final MemberRepository memberRepository;
    private final PromptRepository promptRepository;
    private final PromptVersionRepository versionRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    private static final double WARN_THRESHOLD = 0.8;

    public void checkMemberLimit(UUID workspaceId) {
        Workspace workspace = getWorkspace(workspaceId);

        if (workspace.getPlan() != Workspace.Plan.FREE) {
            return; // No limit for PRO/ENTERPRISE
        }

        long currentCount = memberRepository.countByWorkspaceId(workspaceId);
        if (currentCount >= FREE_MEMBER_LIMIT) {
            throw new BusinessException(ErrorCode.PLAN_LIMIT_EXCEEDED,
                    String.format("Free plan allows maximum %d members. Please upgrade to Pro.", FREE_MEMBER_LIMIT));
        }
    }

    public void checkPromptLimit(UUID workspaceId) {
        Workspace workspace = getWorkspace(workspaceId);

        if (workspace.getPlan() != Workspace.Plan.FREE) {
            return;
        }

        long currentCount = promptRepository.countByWorkspaceId(workspaceId);
        if (currentCount >= FREE_PROMPT_LIMIT) {
            throw new BusinessException(ErrorCode.PLAN_LIMIT_EXCEEDED,
                    String.format("Free plan allows maximum %d prompts. Please upgrade to Pro.", FREE_PROMPT_LIMIT));
        }
        if ((double) (currentCount + 1) / FREE_PROMPT_LIMIT >= WARN_THRESHOLD) {
            eventPublisher.publishEvent(new PlanLimitWarningEvent(
                    workspaceId, "prompts", currentCount + 1, FREE_PROMPT_LIMIT));
        }
    }

    public void checkVersionLimit(UUID promptId, UUID workspaceId) {
        Workspace workspace = getWorkspace(workspaceId);

        if (workspace.getPlan() != Workspace.Plan.FREE) {
            return;
        }

        long currentCount = versionRepository.countByPromptId(promptId);
        if (currentCount >= FREE_VERSION_LIMIT) {
            throw new BusinessException(ErrorCode.PLAN_LIMIT_EXCEEDED,
                    String.format("Free plan allows maximum %d versions per prompt. Please upgrade to Pro.", FREE_VERSION_LIMIT));
        }
    }

    public int getLogRetentionDays(UUID workspaceId) {
        Workspace workspace = getWorkspace(workspaceId);

        if (workspace.getPlan() == Workspace.Plan.FREE) {
            return FREE_LOG_RETENTION_DAYS;
        }
        return UNLIMITED; // No retention limit for PRO/ENTERPRISE
    }

    public UsageResponse getUsage(UUID workspaceId) {
        // Try cache first
        String cacheKey = USAGE_CACHE_PREFIX + workspaceId;
        String cached = redisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            try {
                return objectMapper.readValue(cached, UsageResponse.class);
            } catch (JacksonException e) {
                log.warn("Failed to parse cached usage, fetching fresh data");
            }
        }

        // Fetch fresh data
        Workspace workspace = getWorkspace(workspaceId);
        long memberCount = memberRepository.countByWorkspaceId(workspaceId);
        long promptCount = promptRepository.countByWorkspaceId(workspaceId);

        int memberLimit = workspace.getPlan() == Workspace.Plan.FREE ? FREE_MEMBER_LIMIT : UNLIMITED;
        int promptLimit = workspace.getPlan() == Workspace.Plan.FREE ? FREE_PROMPT_LIMIT : UNLIMITED;
        int logRetentionDays = workspace.getPlan() == Workspace.Plan.FREE ? FREE_LOG_RETENTION_DAYS : UNLIMITED;
        int versionLimit = workspace.getPlan() == Workspace.Plan.FREE ? FREE_VERSION_LIMIT : UNLIMITED;

        UsageResponse usage = UsageResponse.of(
                workspace.getPlan().name(),
                memberCount,
                memberLimit,
                promptCount,
                promptLimit,
                logRetentionDays,
                versionLimit
        );

        // Cache the result
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(usage),
                    CACHE_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
        } catch (JacksonException e) {
            log.warn("Failed to cache usage data");
        }

        return usage;
    }

    public void invalidateUsageCache(UUID workspaceId) {
        String cacheKey = USAGE_CACHE_PREFIX + workspaceId;
        redisTemplate.delete(cacheKey);
    }

    private Workspace getWorkspace(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
    }
}