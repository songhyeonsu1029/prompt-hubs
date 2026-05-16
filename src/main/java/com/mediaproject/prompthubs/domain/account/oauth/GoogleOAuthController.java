package com.mediaproject.prompthubs.domain.account.oauth;

import com.mediaproject.prompthubs.domain.account.dto.TokenResponse;
import com.mediaproject.prompthubs.global.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Tag(name = "OAuth", description = "Social login API")
@RestController
@RequestMapping("/api/v1/auth/oauth/google")
@RequiredArgsConstructor
public class GoogleOAuthController {

    private final GoogleOAuthService googleOAuthService;

    @Operation(summary = "Start Google login", description = "Redirects the browser to Google's authorization page")
    @GetMapping("/login")
    public void login(HttpServletResponse response) throws IOException {
        String url = googleOAuthService.buildAuthorizationUrl();
        response.sendRedirect(url);
    }

    @Operation(summary = "Google OAuth callback",
            description = "Handles the Google authorization code, issues JWT tokens, then redirects to the frontend")
    @GetMapping("/callback")
    public void callback(@RequestParam(required = false) String code,
                         @RequestParam(required = false) String state,
                         @RequestParam(required = false) String error,
                         HttpServletResponse response) throws IOException {
        if (error != null) {
            redirectError(response, error);
            return;
        }
        if (code == null || state == null) {
            redirectError(response, "missing_parameters");
            return;
        }
        try {
            TokenResponse tokens = googleOAuthService.completeOAuth(code, state);
            String target = googleOAuthService.getFrontendCallbackUrl()
                    + "?accessToken=" + URLEncoder.encode(tokens.getAccessToken(), StandardCharsets.UTF_8)
                    + "&refreshToken=" + URLEncoder.encode(tokens.getRefreshToken(), StandardCharsets.UTF_8)
                    + "&expiresIn=" + tokens.getExpiresIn();
            response.sendRedirect(target);
        } catch (BusinessException e) {
            log.warn("Google OAuth callback failed: {}", e.getMessage());
            redirectError(response, e.getMessage());
        } catch (Exception e) {
            log.error("Google OAuth callback unexpected error", e);
            redirectError(response, "internal_error");
        }
    }

    private void redirectError(HttpServletResponse response, String message) throws IOException {
        String target = googleOAuthService.getFrontendLoginUrl()
                + "?oauthError=" + URLEncoder.encode(message, StandardCharsets.UTF_8);
        response.sendRedirect(target);
    }
}
