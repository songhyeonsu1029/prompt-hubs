package com.mediaproject.prompthubs.integration.slack;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "integrations.slack")
public class SlackProperties {

    private boolean enabled;
    private String clientId;
    private String clientSecret;
    private String signingSecret;
    private String redirectUri;
    private String botScope;
}
