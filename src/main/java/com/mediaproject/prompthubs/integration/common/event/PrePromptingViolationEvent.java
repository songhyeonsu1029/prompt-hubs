package com.mediaproject.prompthubs.integration.common.event;

import java.util.UUID;

public record PrePromptingViolationEvent(UUID workspaceId,
                                         UUID promptId,
                                         String promptTitle,
                                         String reason,
                                         String externalUrl) {
}
