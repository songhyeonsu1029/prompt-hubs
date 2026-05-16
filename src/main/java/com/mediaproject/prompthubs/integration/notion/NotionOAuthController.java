package com.mediaproject.prompthubs.integration.notion;

import com.mediaproject.prompthubs.global.security.WorkspaceContextHolder;
import com.mediaproject.prompthubs.integration.notion.dto.NotionPageOption;
import com.mediaproject.prompthubs.integration.notion.dto.SelectParentPageRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class NotionOAuthController {

    private final NotionOAuthService notionOAuthService;

    @GetMapping("/api/v1/w/{slug}/integrations/notion/connect")
    public ResponseEntity<Map<String, String>> connect(@PathVariable String slug) {
        if (WorkspaceContextHolder.getContext() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String url = notionOAuthService.buildAuthorizationUrl(slug);
        return ResponseEntity.ok(Map.of("authorizationUrl", url));
    }

    @GetMapping("/api/v1/integrations/notion/callback")
    public ResponseEntity<String> callback(@RequestParam String code, @RequestParam String state) {
        notionOAuthService.completeOAuth(code, state);
        return ResponseEntity.ok("Notion connected. You can close this window.");
    }

    @PostMapping("/api/v1/w/{slug}/integrations/notion/setup")
    public ResponseEntity<Void> setup(@PathVariable String slug) {
        if (WorkspaceContextHolder.getContext() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        notionOAuthService.manualSetup();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/w/{slug}/integrations/notion/pages")
    public ResponseEntity<List<NotionPageOption>> searchPages(
            @PathVariable String slug,
            @RequestParam(value = "query", required = false) String query) {
        if (WorkspaceContextHolder.getContext() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(notionOAuthService.searchParentPages(query));
    }

    @PutMapping("/api/v1/w/{slug}/integrations/notion/parent-page")
    public ResponseEntity<Void> selectParentPage(
            @PathVariable String slug,
            @Valid @RequestBody SelectParentPageRequest request) {
        if (WorkspaceContextHolder.getContext() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        notionOAuthService.selectParentPage(request.getPageId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/w/{slug}/integrations/notion/backfill")
    public ResponseEntity<Map<String, Integer>> backfill(@PathVariable String slug) {
        if (WorkspaceContextHolder.getContext() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        int pushed = notionOAuthService.backfill();
        return ResponseEntity.ok(Map.of("pushed", pushed));
    }

    @DeleteMapping("/api/v1/w/{slug}/integrations/notion")
    public ResponseEntity<Void> disconnect(@PathVariable String slug) {
        if (WorkspaceContextHolder.getContext() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        notionOAuthService.disconnect();
        return ResponseEntity.noContent().build();
    }
}
