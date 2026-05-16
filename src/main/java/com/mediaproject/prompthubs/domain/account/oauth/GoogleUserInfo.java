package com.mediaproject.prompthubs.domain.account.oauth;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GoogleUserInfo {
    private String sub;
    private String email;
    private boolean emailVerified;
    private String name;
    private String picture;
}
