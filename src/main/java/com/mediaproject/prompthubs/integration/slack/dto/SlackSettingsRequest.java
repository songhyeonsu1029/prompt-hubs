package com.mediaproject.prompthubs.integration.slack.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SlackSettingsRequest {

    @NotBlank
    private String channelId;

    private String channelName;

    private boolean notifyReviewRequested = true;
    private boolean notifyReviewCompleted = true;
    private boolean notifyVersionCreated = true;
    private boolean notifyPlanWarning = true;
    private boolean notifyPrePrompting = true;
}
