package com.mediaproject.prompthubs.integration.slack;

import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.security.WorkspaceContextHolder;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SlackOAuthController {

    private final SlackOAuthService slackOAuthService;

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @GetMapping("/api/v1/w/{slug}/integrations/slack/connect")
    public ResponseEntity<Map<String, String>> connect(@PathVariable String slug) {
        if (WorkspaceContextHolder.getContext() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String url = slackOAuthService.buildAuthorizationUrl(slug);
        return ResponseEntity.ok(Map.of("authorizationUrl", url));
    }

    @GetMapping("/api/v1/integrations/slack/callback")
    public void callback(@RequestParam(required = false) String code,
                         @RequestParam(required = false) String state,
                         @RequestParam(required = false) String error,
                         HttpServletResponse response) throws IOException {
        if (error != null || code == null || state == null) {
            redirectToSettings(response, state, error != null ? error : "missing_parameters");
            return;
        }
        try {
            slackOAuthService.completeOAuth(code, state);
            redirectToSettings(response, state, null);
        } catch (BusinessException e) {
            log.warn("Slack OAuth callback failed: {}", e.getMessage());
            redirectToSettings(response, state, e.getMessage());
        } catch (Exception e) {
            log.error("Slack OAuth callback unexpected error", e);
            redirectToSettings(response, state, "internal_error");
        }
    }

    @DeleteMapping("/api/v1/w/{slug}/integrations/slack")
    public ResponseEntity<Void> disconnect(@PathVariable String slug) {
        // WorkspaceAccessFilter sets the context based on slug
        if (WorkspaceContextHolder.getContext() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        slackOAuthService.disconnect();
        return ResponseEntity.noContent().build();
    }

    private void redirectToSettings(HttpServletResponse response, String slug, String error) throws IOException {
        String base = frontendBaseUrl.replaceAll("/+$", "");
        String safeSlug = slug == null ? "" : URLEncoder.encode(slug, StandardCharsets.UTF_8);
        StringBuilder target = new StringBuilder(base)
                .append("/w/").append(safeSlug).append("/settings?tab=integrations");
        if (error != null) {
            target.append("&slackError=").append(URLEncoder.encode(error, StandardCharsets.UTF_8));
        } else {
            target.append("&slackConnected=1");
        }
        response.sendRedirect(target.toString());
    }
}
