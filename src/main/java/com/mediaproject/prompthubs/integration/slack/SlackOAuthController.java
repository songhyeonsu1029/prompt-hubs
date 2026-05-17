package com.mediaproject.prompthubs.integration.slack;

import com.mediaproject.prompthubs.global.security.WorkspaceContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SlackOAuthController {

    private final SlackOAuthService slackOAuthService;

    @GetMapping("/api/v1/w/{slug}/integrations/slack/connect")
    public ResponseEntity<Map<String, String>> connect(@PathVariable String slug) {
        if (WorkspaceContextHolder.getContext() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String url = slackOAuthService.buildAuthorizationUrl(slug);
        return ResponseEntity.ok(Map.of("authorizationUrl", url));
    }

    @GetMapping("/api/v1/integrations/slack/callback")
    public ResponseEntity<String> callback(@RequestParam String code, @RequestParam String state) {
        slackOAuthService.completeOAuth(code, state);
        return ResponseEntity.ok("Slack connected. You can close this window.");
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
}
