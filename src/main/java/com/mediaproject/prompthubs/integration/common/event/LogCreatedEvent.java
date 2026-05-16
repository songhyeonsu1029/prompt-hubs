package com.mediaproject.prompthubs.integration.common.event;

import com.mediaproject.prompthubs.domain.log.entity.PromptLog;

import java.util.UUID;

public record LogCreatedEvent(UUID workspaceId, UUID logId, PromptLog log) {
}
