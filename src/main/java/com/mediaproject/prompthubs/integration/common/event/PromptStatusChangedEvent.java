package com.mediaproject.prompthubs.integration.common.event;

import com.mediaproject.prompthubs.domain.prompt.entity.Prompt;

import java.util.UUID;

public record PromptStatusChangedEvent(UUID workspaceId,
                                       UUID promptId,
                                       Prompt prompt,
                                       Prompt.Status oldStatus,
                                       Prompt.Status newStatus) {
}
