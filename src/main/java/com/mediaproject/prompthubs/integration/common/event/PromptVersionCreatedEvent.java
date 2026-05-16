package com.mediaproject.prompthubs.integration.common.event;

import com.mediaproject.prompthubs.domain.prompt.entity.Prompt;
import com.mediaproject.prompthubs.domain.prompt.entity.PromptVersion;

import java.util.UUID;

public record PromptVersionCreatedEvent(UUID workspaceId,
                                        UUID promptId,
                                        Prompt prompt,
                                        PromptVersion version) {
}
