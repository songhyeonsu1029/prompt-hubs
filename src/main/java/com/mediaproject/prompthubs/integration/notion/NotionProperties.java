package com.mediaproject.prompthubs.integration.notion;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "integrations.notion")
public class NotionProperties {

    private boolean enabled;
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String apiBase;
    private String apiVersion;
    private long pollIntervalSeconds;
    private String parentPageId;
}
