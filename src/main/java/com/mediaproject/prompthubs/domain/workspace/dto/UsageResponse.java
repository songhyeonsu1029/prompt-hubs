package com.mediaproject.prompthubs.domain.workspace.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UsageResponse {

    private String plan;
    private long memberCount;
    private int memberLimit;
    private long promptCount;
    private int promptLimit;
    private int logRetentionDays;
    private int versionLimit;

    public static UsageResponse of(String plan, long memberCount, int memberLimit,
            long promptCount, int promptLimit, int logRetentionDays, int versionLimit) {
        return UsageResponse.builder()
                .plan(plan)
                .memberCount(memberCount)
                .memberLimit(memberLimit)
                .promptCount(promptCount)
                .promptLimit(promptLimit)
                .logRetentionDays(logRetentionDays)
                .versionLimit(versionLimit)
                .build();
    }
}