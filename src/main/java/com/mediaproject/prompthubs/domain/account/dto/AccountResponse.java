package com.mediaproject.prompthubs.domain.account.dto;

import com.mediaproject.prompthubs.domain.account.entity.Account;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class AccountResponse {

    private UUID id;
    private String email;
    private String name;
    private String provider;
    private String avatarUrl;
    private LocalDateTime createdAt;

    public static AccountResponse from(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .email(account.getEmail())
                .name(account.getName())
                .provider(account.getProvider().name())
                .avatarUrl(account.getAvatarUrl())
                .createdAt(account.getCreatedAt())
                .build();
    }
}