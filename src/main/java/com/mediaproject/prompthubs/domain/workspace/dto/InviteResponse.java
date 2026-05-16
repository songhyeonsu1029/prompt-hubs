package com.mediaproject.prompthubs.domain.workspace.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class InviteResponse {

    private String token;
    private String inviteUrl;
    private LocalDateTime expiresAt;

    public static InviteResponse of(String token, String slug, LocalDateTime expiresAt) {
        String inviteUrl = String.format("/api/v1/workspaces/%s/join?token=%s", slug, token);
        return InviteResponse.builder()
                .token(token)
                .inviteUrl(inviteUrl)
                .expiresAt(expiresAt)
                .build();
    }
}