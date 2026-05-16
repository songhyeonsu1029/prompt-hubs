package com.mediaproject.prompthubs.integration.common.repository;

import com.mediaproject.prompthubs.integration.common.entity.IntegrationType;
import com.mediaproject.prompthubs.integration.common.entity.OutboundRetry;
import com.mediaproject.prompthubs.integration.common.entity.SyncRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboundRetryRepository extends JpaRepository<OutboundRetry, UUID> {

    Optional<OutboundRetry> findByWorkspaceIdAndEntityTypeAndEntityIdAndPlatform(
            UUID workspaceId,
            SyncRecord.EntityType entityType,
            UUID entityId,
            IntegrationType platform);

    List<OutboundRetry> findTop50BySucceededFalseAndTerminalFalseAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(
            LocalDateTime cutoff);
}
