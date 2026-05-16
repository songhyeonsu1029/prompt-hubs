package com.mediaproject.prompthubs.integration.common.repository;

import com.mediaproject.prompthubs.integration.common.entity.IntegrationType;
import com.mediaproject.prompthubs.integration.common.entity.SyncRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SyncRecordRepository extends JpaRepository<SyncRecord, UUID> {

    Optional<SyncRecord> findByWorkspaceIdAndEntityTypeAndEntityIdAndExternalPlatform(
            UUID workspaceId,
            SyncRecord.EntityType entityType,
            UUID entityId,
            IntegrationType externalPlatform
    );

    Optional<SyncRecord> findByExternalPlatformAndExternalId(
            IntegrationType externalPlatform,
            String externalId
    );

    List<SyncRecord> findByWorkspaceIdAndExternalPlatform(UUID workspaceId, IntegrationType externalPlatform);

    /**
     * Atomically reserves the sync slot for (workspace, entity, platform) by inserting a placeholder
     * row. Returns 1 if this caller won the race, 0 if another transaction already has it. Backed by
     * the PostgreSQL unique index uk_sync_record_entity_platform — no exception is raised on conflict
     * so the caller's transaction stays clean.
     */
    @Modifying
    @Query(value = """
            INSERT INTO sync_records (
                id, created_at, updated_at,
                workspace_id, entity_type, entity_id,
                external_id, external_platform, last_synced_at
            ) VALUES (
                gen_random_uuid(), NOW(), NOW(),
                :workspaceId, :entityType, :entityId,
                :externalId, :externalPlatform, NOW()
            )
            ON CONFLICT (workspace_id, entity_type, entity_id, external_platform) DO NOTHING
            """, nativeQuery = true)
    int tryInsertPlaceholder(@Param("workspaceId") UUID workspaceId,
                             @Param("entityType") String entityType,
                             @Param("entityId") UUID entityId,
                             @Param("externalId") String externalId,
                             @Param("externalPlatform") String externalPlatform);
}
