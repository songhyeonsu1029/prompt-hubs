package com.mediaproject.prompthubs.integration.common.entity;

import com.mediaproject.prompthubs.domain.workspace.entity.Workspace;
import com.mediaproject.prompthubs.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sync_records",
        indexes = {
                @Index(name = "idx_sync_internal", columnList = "workspace_id,entity_type,entity_id"),
                @Index(name = "idx_sync_external", columnList = "external_platform,external_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_sync_record_entity_platform",
                        columnNames = {"workspace_id", "entity_type", "entity_id", "external_platform"}
                )
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SyncRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 16)
    private EntityType entityType;

    @Column(name = "entity_id", nullable = false, columnDefinition = "uuid")
    private UUID entityId;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "external_platform", nullable = false, length = 16)
    private IntegrationType externalPlatform;

    @Column(name = "last_synced_at", nullable = false)
    private LocalDateTime lastSyncedAt;

    @Column(name = "last_synced_hash", length = 100)
    private String lastSyncedHash;

    public enum EntityType {
        LOG, DOC, PROMPT
    }

    public void touch(String hash) {
        this.lastSyncedAt = LocalDateTime.now();
        if (hash != null) {
            this.lastSyncedHash = hash;
        }
    }

    public void promoteFromPlaceholder(String realExternalId, String hash) {
        this.externalId = realExternalId;
        touch(hash);
    }

    public boolean isPlaceholder() {
        return externalId != null && externalId.startsWith(PLACEHOLDER_PREFIX);
    }

    public static final String PLACEHOLDER_PREFIX = "PENDING:";
}
