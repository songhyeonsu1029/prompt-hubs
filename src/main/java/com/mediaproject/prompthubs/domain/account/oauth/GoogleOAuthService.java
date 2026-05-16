package com.mediaproject.prompthubs.domain.account.oauth;

import com.mediaproject.prompthubs.domain.account.dto.TokenResponse;
import com.mediaproject.prompthubs.domain.account.entity.Account;
import com.mediaproject.prompthubs.domain.account.repository.AccountRepository;
import com.mediaproject.prompthubs.global.config.JwtConfig;
import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import com.mediaproject.prompthubs.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String STATE_PREFIX = "oauth_state:google:";
    private static final long STATE_TTL_SECONDS = 300;

    private final GoogleOAuthProperties properties;
    private final GoogleApiClient apiClient;
    private final AccountRepository accountRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtConfig jwtConfig;
    private final RedisTemplate<String, String> redisTemplate;

    public String buildAuthorizationUrl() {
        ensureEnabled();
        String state = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(STATE_PREFIX + state, "1", STATE_TTL_SECONDS, TimeUnit.SECONDS);

        String redirect = URLEncoder.encode(properties.getRedirectUri(), StandardCharsets.UTF_8);
        String scope = URLEncoder.encode(properties.getScope(), StandardCharsets.UTF_8);
        return AUTH_URL
                + "?response_type=code"
                + "&client_id=" + properties.getClientId()
                + "&redirect_uri=" + redirect
                + "&scope=" + scope
                + "&access_type=online"
                + "&prompt=select_account"
                + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);
    }

    @Transactional
    public TokenResponse completeOAuth(String code, String state) {
        ensureEnabled();
        validateState(state);

        JsonNode tokenResp = apiClient.exchangeCode(code);
        String googleAccessToken = tokenResp.path("access_token").asText(null);
        if (googleAccessToken == null) {
            throw new BusinessException(ErrorCode.INTEGRATION_OAUTH_FAILED, "Missing access_token from Google");
        }

        GoogleUserInfo info = apiClient.fetchUserInfo(googleAccessToken);
        if (info.getSub() == null || info.getEmail() == null) {
            throw new BusinessException(ErrorCode.INTEGRATION_OAUTH_FAILED, "Incomplete Google userinfo");
        }
        if (!info.isEmailVerified()) {
            throw new BusinessException(ErrorCode.INTEGRATION_OAUTH_FAILED, "Google email is not verified");
        }

        Account account = findOrCreateAccount(info);

        String accessToken = jwtTokenProvider.createAccessToken(account.getId(), account.getEmail());
        String refreshToken = jwtTokenProvider.createRefreshToken(account.getId());
        return TokenResponse.of(accessToken, refreshToken, jwtConfig.getAccessExpiration() / 1000);
    }

    private Account findOrCreateAccount(GoogleUserInfo info) {
        Optional<Account> byProvider = accountRepository
                .findByProviderAndProviderId(Account.Provider.GOOGLE, info.getSub());
        if (byProvider.isPresent()) {
            Account existing = byProvider.get();
            existing.updateProfile(info.getName(), info.getPicture());
            return existing;
        }

        Optional<Account> byEmail = accountRepository.findByEmail(info.getEmail());
        if (byEmail.isPresent()) {
            Account existing = byEmail.get();
            if (existing.getProvider() != Account.Provider.GOOGLE) {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS,
                        "This email is already registered with " + existing.getProvider().name()
                                + " login. Please sign in using that method.");
            }
            existing.updateProfile(info.getName(), info.getPicture());
            return existing;
        }

        Account created = Account.builder()
                .email(info.getEmail())
                .name(info.getName() == null ? info.getEmail() : info.getName())
                .provider(Account.Provider.GOOGLE)
                .providerId(info.getSub())
                .avatarUrl(info.getPicture())
                .build();
        Account saved = accountRepository.save(created);
        log.info("New Google account created: {}", saved.getEmail());
        return saved;
    }

    public String getFrontendCallbackUrl() {
        return properties.getFrontendCallbackUrl();
    }

    public String getFrontendLoginUrl() {
        return properties.getFrontendLoginUrl();
    }

    public boolean isEnabled() {
        return properties.isEnabled()
                && properties.getClientId() != null && !properties.getClientId().isBlank();
    }

    private void validateState(String state) {
        if (state == null || state.isBlank()) {
            throw new BusinessException(ErrorCode.INTEGRATION_OAUTH_FAILED, "Missing state");
        }
        String key = STATE_PREFIX + state;
        Boolean deleted = redisTemplate.delete(key);
        if (deleted == null || !deleted) {
            throw new BusinessException(ErrorCode.INTEGRATION_OAUTH_FAILED, "Invalid or expired state");
        }
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.INTEGRATION_DISABLED, "Google OAuth is disabled");
        }
        if (properties.getClientId() == null || properties.getClientId().isBlank()
                || properties.getClientSecret() == null || properties.getClientSecret().isBlank()) {
            throw new BusinessException(ErrorCode.INTEGRATION_DISABLED, "Google OAuth credentials not configured");
        }
    }
}
