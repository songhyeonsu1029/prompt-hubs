package com.mediaproject.prompthubs.integration.slack.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SlackChannelResponse {
    private String id;
    private String name;
    private boolean privateChannel;
}
