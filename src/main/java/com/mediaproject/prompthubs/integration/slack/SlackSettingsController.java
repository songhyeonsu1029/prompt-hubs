package com.mediaproject.prompthubs.integration.slack;

import com.mediaproject.prompthubs.global.security.WorkspaceContextHolder;
import com.mediaproject.prompthubs.integration.slack.dto.SlackChannelResponse;
import com.mediaproject.prompthubs.integration.slack.dto.SlackSettingsRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/w/{slug}/integrations/slack")
@RequiredArgsConstructor
public class SlackSettingsController {

    private final SlackSettingsService slackSettingsService;

    @GetMapping("/channels")
    public ResponseEntity<List<SlackChannelResponse>> channels(@PathVariable String slug) {
        if (WorkspaceContextHolder.getContext() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(slackSettingsService.listChannels());
    }

    @PutMapping("/settings")
    public ResponseEntity<Void> updateSettings(@PathVariable String slug,
                                               @Valid @RequestBody SlackSettingsRequest request) {
        if (WorkspaceContextHolder.getContext() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        slackSettingsService.updateSettings(request);
        return ResponseEntity.noContent().build();
    }
}
