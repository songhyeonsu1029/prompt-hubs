package com.mediaproject.prompthubs.integration.notion;

import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import com.mediaproject.prompthubs.integration.common.OutboundRetryService;
import com.mediaproject.prompthubs.integration.common.entity.IntegrationType;
import com.mediaproject.prompthubs.integration.common.entity.OutboundRetry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically drains the {@link OutboundRetry} queue for the Notion platform.
 * Each retry runs in its own transaction (via NotionOutboundSyncService.retrySync) so a
 * failing item never aborts the rest of the batch.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotionOutboundRetryScheduler {

    private final OutboundRetryService retryService;
    private final NotionOutboundSyncService outboundSync;

    @Scheduled(fixedDelayString = "${integrations.notion.retry-interval-seconds:30}000")
    public void drain() {
        for (OutboundRetry item : retryService.fetchDue()) {
            if (item.getPlatform() != IntegrationType.NOTION) continue;
            try {
                outboundSync.retrySync(
                        item.getWorkspace().getId(),
                        item.getEntityType(),
                        item.getEntityId());
                retryService.markSucceeded(item.getId());
                log.info("Retry succeeded: id={} type={} entity={}",
                        item.getId(), item.getEntityType(), item.getEntityId());
            } catch (BusinessException e) {
                if (e.getErrorCode() == ErrorCode.INTEGRATION_OAUTH_FAILED) {
                    // The integration was deactivated by the listener-level catch in
                    // NotionOutboundSyncService.handleFailure. No point retrying.
                    retryService.markTerminal(item.getId(),
                            "OAuth failed; integration deactivated");
                } else {
                    // Re-queue with a longer backoff.
                    retryService.queue(
                            item.getWorkspace().getId(),
                            item.getEntityType(),
                            item.getEntityId(),
                            IntegrationType.NOTION,
                            e.getMessage());
                }
            } catch (Exception e) {
                retryService.queue(
                        item.getWorkspace().getId(),
                        item.getEntityType(),
                        item.getEntityId(),
                        IntegrationType.NOTION,
                        e.getMessage());
            }
        }
    }
}
