package com.mediaproject.prompthubs.domain.log.service;

import com.mediaproject.prompthubs.domain.account.entity.Account;
import com.mediaproject.prompthubs.domain.account.repository.AccountRepository;
import com.mediaproject.prompthubs.domain.log.dto.*;
import com.mediaproject.prompthubs.domain.log.entity.PromptLog;
import com.mediaproject.prompthubs.domain.log.repository.PromptLogRepository;
import com.mediaproject.prompthubs.domain.workspace.entity.Workspace;
import com.mediaproject.prompthubs.domain.workspace.repository.WorkspaceRepository;
import com.mediaproject.prompthubs.domain.workspace.service.PlanLimitService;
import com.mediaproject.prompthubs.global.common.PageResponse;
import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import com.mediaproject.prompthubs.global.security.WorkspaceContext;
import com.mediaproject.prompthubs.global.security.WorkspaceContextHolder;
import com.mediaproject.prompthubs.integration.common.event.LogCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LogService {

    private final PromptLogRepository logRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AccountRepository accountRepository;
    private final PlanLimitService planLimitService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public LogResponse create(CreateLogRequest request, UUID accountId) {
        WorkspaceContext context = getWorkspaceContext();
        UUID workspaceId = context.getWorkspaceId();

        // Fast-path idempotency: if the caller supplied a key and we've already stored
        // a row for it, return the existing row without firing another LogCreatedEvent.
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<PromptLog> existing = logRepository
                    .findByWorkspaceIdAndIdempotencyKey(workspaceId, request.getIdempotencyKey());
            if (existing.isPresent()) {
                return LogResponse.from(existing.get());
            }
        }

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));

        Account author = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        PromptLog promptLog = PromptLog.builder()
                .workspace(workspace)
                .author(author)
                .promptText(request.getPromptText())
                .modelUsed(request.getModelUsed())
                .resultSummary(request.getResultSummary())
                .resultBody(request.getResultBody())
                .status(request.getStatus())
                .tags(request.getTags() != null ? request.getTags() : new ArrayList<>())
                .sessionId(request.getSessionId())
                .idempotencyKey(request.getIdempotencyKey())
                .metadata(request.getMetadata())
                .build();

        try {
            PromptLog savedLog = logRepository.saveAndFlush(promptLog);
            log.info("Log created: {} in workspace {}", savedLog.getId(), context.getWorkspaceSlug());
            eventPublisher.publishEvent(new LogCreatedEvent(workspace.getId(), savedLog.getId(), savedLog));
            return LogResponse.from(savedLog);
        } catch (DataIntegrityViolationException conflict) {
            // Concurrent insert with the same idempotency key — a sibling thread won.
            // Return the row they wrote so the caller treats both calls as a single create.
            if (request.getIdempotencyKey() != null) {
                PromptLog winner = logRepository
                        .findByWorkspaceIdAndIdempotencyKey(workspaceId, request.getIdempotencyKey())
                        .orElseThrow(() -> conflict);
                return LogResponse.from(winner);
            }
            throw conflict;
        }
    }

    public PageResponse<LogResponse> getLogs(PromptLog.Status status, String keyword, UUID authorId, Pageable pageable) {
        WorkspaceContext context = getWorkspaceContext();
        UUID workspaceId = context.getWorkspaceId();
        Page<PromptLog> logs;

        int retentionDays = planLimitService.getLogRetentionDays(workspaceId);
        LocalDateTime cutoff = retentionDays == -1
                ? null
                : LocalDateTime.now().minusDays(retentionDays);

        if (cutoff != null) {
            logs = getLogsWithRetention(workspaceId, status, keyword, authorId, cutoff, pageable);
        } else {
            logs = getLogsWithoutRetention(workspaceId, status, keyword, authorId, pageable);
        }

        Page<LogResponse> responsePage = logs.map(LogResponse::from);
        return PageResponse.of(responsePage);
    }

    private Page<PromptLog> getLogsWithRetention(UUID workspaceId, PromptLog.Status status,
            String keyword, UUID authorId, LocalDateTime cutoff, Pageable pageable) {
        if (status != null) {
            return logRepository.findByWorkspaceIdAndStatusAndCreatedAtAfter(workspaceId, status, cutoff, pageable);
        } else if (keyword != null && !keyword.isBlank()) {
            return logRepository.findByWorkspaceIdAndKeywordAndCreatedAtAfter(workspaceId, keyword, cutoff, pageable);
        } else if (authorId != null) {
            return logRepository.findByWorkspaceIdAndAuthorIdAndCreatedAtAfter(workspaceId, authorId, cutoff, pageable);
        } else {
            return logRepository.findByWorkspaceIdAndCreatedAtAfter(workspaceId, cutoff, pageable);
        }
    }

    private Page<PromptLog> getLogsWithoutRetention(UUID workspaceId, PromptLog.Status status,
            String keyword, UUID authorId, Pageable pageable) {
        if (status != null) {
            return logRepository.findByWorkspaceIdAndStatus(workspaceId, status, pageable);
        } else if (keyword != null && !keyword.isBlank()) {
            return logRepository.findByWorkspaceIdAndKeyword(workspaceId, keyword, pageable);
        } else if (authorId != null) {
            return logRepository.findByWorkspaceIdAndAuthorId(workspaceId, authorId, pageable);
        } else {
            return logRepository.findByWorkspaceId(workspaceId, pageable);
        }
    }

    public LogResponse getLog(UUID logId) {
        WorkspaceContext context = getWorkspaceContext();

        PromptLog promptLog = logRepository.findByIdAndWorkspaceId(logId, context.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Log not found"));

        // Check retention policy
        int retentionDays = planLimitService.getLogRetentionDays(context.getWorkspaceId());
        if (retentionDays != -1) {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
            if (promptLog.getCreatedAt().isBefore(cutoff)) {
                throw new BusinessException(ErrorCode.PLAN_LIMIT_EXCEEDED,
                        "This log has exceeded the retention period. Upgrade to Pro for unlimited retention.");
            }
        }

        return LogResponse.from(promptLog);
    }

    @Transactional
    public LogResponse update(UUID logId, UpdateLogRequest request) {
        WorkspaceContext context = getWorkspaceContext();

        PromptLog promptLog = logRepository.findByIdAndWorkspaceId(logId, context.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Log not found"));

        promptLog.update(
                request.getPromptText(),
                request.getModelUsed(),
                request.getResultSummary(),
                request.getStatus(),
                request.getTags()
        );

        log.info("Log updated: {} in workspace {}", logId, context.getWorkspaceSlug());

        return LogResponse.from(promptLog);
    }

    @Transactional
    public void delete(UUID logId) {
        WorkspaceContext context = getWorkspaceContext();

        PromptLog promptLog = logRepository.findByIdAndWorkspaceId(logId, context.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Log not found"));

        logRepository.delete(promptLog);
        log.info("Log deleted: {} in workspace {}", logId, context.getWorkspaceSlug());
    }

    // For promote feature
    public PromptLog getLogEntity(UUID logId, UUID workspaceId) {
        return logRepository.findByIdAndWorkspaceId(logId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Log not found"));
    }

    private WorkspaceContext getWorkspaceContext() {
        WorkspaceContext context = WorkspaceContextHolder.getContext();
        if (context == null) {
            throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND, "Workspace context not found");
        }
        return context;
    }
}