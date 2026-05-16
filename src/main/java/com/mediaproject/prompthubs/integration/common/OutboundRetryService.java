package com.mediaproject.prompthubs.integration.common;

import com.mediaproject.prompthubs.domain.workspace.entity.Workspace;
import com.mediaproject.prompthubs.domain.workspace.repository.WorkspaceRepository;
import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import com.mediaproject.prompthubs.integration.common.entity.IntegrationType;
import com.mediaproject.prompthubs.integration.common.entity.OutboundRetry;
import com.mediaproject.prompthubs.integration.common.entity.SyncRecord;
import com.mediaproject.prompthubs.integration.common.repository.OutboundRetryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Manages the persistent {@link OutboundRetry} queue. All write operations run in a fresh
 * transaction so a caller whose own transaction has been marked rollback-only (e.g. after a
 * failed Notion API call) can still record the retry without losing it on rollback.
 *
 * Backoff is exponential with a 1-hour ceiling: 30s, 1m, 5m, 30m, 1h, 1h, …
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboundRetryService {

    private static final Duration[] BACKOFF = {
            Duration.ofSeconds(30),
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(30),
            Duration.ofHours(1)
    };

    private final OutboundRetryRepository repository;
    private final WorkspaceRepository workspaceRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void queue(UUID workspaceId, SyncRecord.EntityType type, UUID entityId,
                      IntegrationType platform, String error) {
        OutboundRetry row = repository
                .findByWorkspaceIdAndEntityTypeAndEntityIdAndPlatform(workspaceId, type, entityId, platform)
                .orElse(null);

        if (row == null) {
            Workspace workspace = workspaceRepository.findById(workspaceId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
            row = OutboundRetry.builder()
                    .workspace(workspace)
                    .entityType(type)
                    .entityId(entityId)
                    .platform(platform)
                    .nextAttemptAt(LocalDateTime.now().plus(BACKOFF[0]))
                    .lastError(error == null ? null : error.substring(0, Math.min(2000, error.length())))
                    .build();
            repository.save(row);
            log.info("Queued outbound retry: workspace={} type={} id={} attempt=0", workspaceId, type, entityId);
            return;
        }

        if (row.isSucceeded()) {
            // Was already succeeded; this is a fresh failure on a later push. Reset and re-queue.
            row.recordFailure(error, LocalDateTime.now().plus(BACKOFF[0]));
            // Reset the exhausted flags so the row will be picked up again.
            // recordFailure() touches attempts/lastError/nextAttemptAt; we need to flip succeeded.
            // No public setter exists, so prefer creating a fresh row by deletion + insert.
            repository.delete(row);
            queue(workspaceId, type, entityId, platform, error);
            return;
        }

        if (row.isTerminal()) {
            // Operator must clear terminal rows manually.
            log.debug("Skipping queue for terminal retry row {}", row.getId());
            return;
        }

        Duration delay = BACKOFF[Math.min(row.getAttempts(), BACKOFF.length - 1)];
        row.recordFailure(error, LocalDateTime.now().plus(delay));
        log.info("Updated outbound retry: workspace={} type={} id={} attempts={} terminal={}",
                workspaceId, type, entityId, row.getAttempts(), row.isTerminal());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSucceeded(UUID retryId) {
        repository.findById(retryId).ifPresent(OutboundRetry::markSucceeded);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markTerminal(UUID retryId, String reason) {
        repository.findById(retryId).ifPresent(r -> r.markTerminal(reason));
    }

    @Transactional(readOnly = true)
    public List<OutboundRetry> fetchDue() {
        return repository
                .findTop50BySucceededFalseAndTerminalFalseAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(
                        LocalDateTime.now());
    }
}
