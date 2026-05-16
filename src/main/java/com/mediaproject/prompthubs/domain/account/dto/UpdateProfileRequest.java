package com.mediaproject.prompthubs.domain.account.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateProfileRequest {

    private String name;
    private String avatarUrl;
}