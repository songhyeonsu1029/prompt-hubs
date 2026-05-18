package com.mediaproject.prompthubs.integration.common.repository;

import com.mediaproject.prompthubs.integration.common.entity.IntegrationType;
import com.mediaproject.prompthubs.integration.common.entity.WorkspaceIntegration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceIntegrationRepository extends JpaRepository<WorkspaceIntegration, UUID> {

    Optional<WorkspaceIntegration> findByWorkspaceIdAndType(UUID workspaceId, IntegrationType type);

    List<WorkspaceIntegration> findByWorkspaceId(UUID workspaceId);

    List<WorkspaceIntegration> findByTypeAndActiveTrue(IntegrationType type);

    // Paired with the slack_team_id UNIQUE constraint on workspace_integrations.
    // OrderByCreatedAtDesc keeps callers safe even before the constraint is in place
    // (legacy/duplicate rows) by always returning the most recently created row.
    Optional<WorkspaceIntegration> findFirstBySlackTeamIdAndTypeOrderByCreatedAtDesc(
            String slackTeamId, IntegrationType type);
}
