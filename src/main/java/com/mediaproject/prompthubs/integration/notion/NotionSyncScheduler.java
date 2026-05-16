package com.mediaproject.prompthubs.integration.notion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "integrations.notion", name = "enabled", havingValue = "true")
public class NotionSyncScheduler {

    private final NotionInboundSyncService inboundSyncService;

    @Scheduled(fixedDelayString = "${integrations.notion.poll-interval-seconds:60}000",
            initialDelay = 30_000L)
    public void poll() {
        log.debug("Running Notion inbound sync");
        inboundSyncService.syncAll();
    }
}
