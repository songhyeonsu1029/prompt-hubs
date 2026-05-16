package com.mediaproject.prompthubs.domain.account.oauth;

import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.JsonNode;

@Slf4j
@Component
public class GoogleApiClient {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    private final WebClient webClient;
    private final GoogleOAuthProperties properties;

    public GoogleApiClient(GoogleOAuthProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder().build();
    }

    public JsonNode exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("redirect_uri", properties.getRedirectUri());
        try {
            return webClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Google token exchange failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.INTEGRATION_OAUTH_FAILED,
                    "Google token exchange failed: " + e.getStatusCode());
        }
    }

    public GoogleUserInfo fetchUserInfo(String accessToken) {
        try {
            JsonNode body = webClient.get()
                    .uri(USERINFO_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            if (body == null) {
                throw new BusinessException(ErrorCode.INTEGRATION_OAUTH_FAILED, "Empty userinfo response");
            }
            return GoogleUserInfo.builder()
                    .sub(body.path("sub").asText(null))
                    .email(body.path("email").asText(null))
                    .emailVerified(body.path("email_verified").asBoolean(false))
                    .name(body.path("name").asText(null))
                    .picture(body.path("picture").asText(null))
                    .build();
        } catch (WebClientResponseException e) {
            log.error("Google userinfo fetch failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.INTEGRATION_OAUTH_FAILED,
                    "Google userinfo fetch failed: " + e.getStatusCode());
        }
    }
}
