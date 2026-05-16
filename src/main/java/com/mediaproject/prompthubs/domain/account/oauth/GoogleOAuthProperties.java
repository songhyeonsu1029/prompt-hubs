package com.mediaproject.prompthubs.domain.account.oauth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "oauth.google")
public class GoogleOAuthProperties {

    private boolean enabled;
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String scope = "openid email profile";
    private String frontendCallbackUrl;
    private String frontendLoginUrl;
}
