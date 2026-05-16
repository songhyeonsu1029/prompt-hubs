package com.mediaproject.prompthubs.integration.common.entity;

import com.mediaproject.prompthubs.domain.workspace.entity.Workspace;
import com.mediaproject.prompthubs.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persistent retry queue for outbound integration writes (Notion, Slack, …).
 * One row per (workspace, entity, platform) — re-failures upsert via the unique
 * constraint, so the queue never bloats with duplicates of the same target.
 */
@Entity
@Table(name = "outbound_retries",
        indexes = {
                @Index(name = "idx_outbound_retry_due",
                        columnList = "succeeded,next_attempt_at"),
                @Index(name = "idx_outbound_retry_entity",
                        columnList = "workspace_id,entity_type,entity_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_outbound_retry_target",
                        columnNames = {"workspace_id", "entity_type", "entity_id", "platform"}
                )
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class OutboundRetry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 16)
    private SyncRecord.EntityType entityType;

    @Column(name = "entity_id", nullable = false, columnDefinition = "uuid")
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 16)
    private IntegrationType platform;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private int maxAttempts = 5;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "succeeded", nullable = false)
    @Builder.Default
    private boolean succeeded = false;

    @Column(name = "terminal", nullable = false)
    @Builder.Default
    private boolean terminal = false;

    public void recordFailure(String error, LocalDateTime nextAttempt) {
        this.attempts += 1;
        this.lastError = truncate(error, 2000);
        this.nextAttemptAt = nextAttempt;
        if (this.attempts >= this.maxAttempts) {
            this.terminal = true;
        }
    }

    public void markSucceeded() {
        this.succeeded = true;
        this.lastError = null;
    }

    public void markTerminal(String error) {
        this.terminal = true;
        this.lastError = truncate(error, 2000);
    }

    public boolean isExhausted() {
        return terminal || succeeded || attempts >= maxAttempts;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
