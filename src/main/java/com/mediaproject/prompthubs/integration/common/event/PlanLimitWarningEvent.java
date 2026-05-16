package com.mediaproject.prompthubs.integration.common.event;

import java.util.UUID;

public record PlanLimitWarningEvent(UUID workspaceId,
                                    String resource,
                                    long currentUsage,
                                    long limit) {
}
