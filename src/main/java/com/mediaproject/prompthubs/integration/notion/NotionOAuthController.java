package com.mediaproject.prompthubs.integration.notion;

import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.security.WorkspaceContextHolder;
import com.mediaproject.prompthubs.integration.notion.dto.NotionPageOption;
import com.mediaproject.prompthubs.integration.notion.dto.SelectParentPageRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class NotionOAuthController {

    private final NotionOAuthService notionOAuthService;

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @GetMapping("/api/v1/w/{slug}/integrations/notion/connect")
    public ResponseEntity<Map<String, String>> connect(@PathVariable String slug) {
        if (WorkspaceContextHolder.getContext() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String url = notionOAuthService.buildAuthorizationUrl(slug);
        return ResponseEntity.ok(Map.of("authorizationUrl", url));
    }

    @GetMapping("/api/v1/integrations/notion/callback")
    public void callback(@RequestParam(required = false) String code,
                         @RequestParam(required = false) String state,
                         @RequestParam(required = false) String error,
                         HttpServletResponse response) throws IOException {
        if (error != null || code == null || state == null) {
            redirectToSettings(response, state, error != null ? error : "missing_parameters");
            return;
        }
        try {
            notionOAuthService.completeOAuth(code, state);
            redirectToSettings(response, state, null);
        } catch (BusinessException e) {
            log.warn("Notion OAuth callback failed: {}", e.getMessage());
            redirectToSettings(response, state, e.getMessage());
        } catch (Exception e) {
            log.error("Notion OAuth callback unexpected error", e);
            redirectToSettings(response, state, "internal_error");
        }
    }

    private void redirectToSettings(HttpServletResponse response, String slug, String error) throws IOException {
        String base = frontendBaseUrl.replaceAll("/+$", "");
        String safeSlug = slug == null ? "" : URLEncoder.encode(slug, StandardCharsets.UTF_8);
        StringBuilder target = new StringBuilder(base)
                .append("/w/").append(safeSlug).append("/settings?tab=integrations");
        if (error != null) {
            target.append("&notionError=").append(URLEncoder.encode(error, StandardCharsets.UTF_8));
        } else {
            target.append("&notionConnected=1");
        }
        response.sendRedirect(target.toString());
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
