package com.mediaproject.prompthubs.integration.notion;

import com.mediaproject.prompthubs.domain.doc.entity.Doc;
import com.mediaproject.prompthubs.domain.doc.repository.DocRepository;
import com.mediaproject.prompthubs.domain.log.entity.PromptLog;
import com.mediaproject.prompthubs.domain.log.repository.PromptLogRepository;
import com.mediaproject.prompthubs.domain.prompt.entity.Prompt;
import com.mediaproject.prompthubs.domain.prompt.entity.PromptVersion;
import com.mediaproject.prompthubs.domain.prompt.repository.PromptRepository;
import com.mediaproject.prompthubs.domain.prompt.repository.PromptVersionRepository;
import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import com.mediaproject.prompthubs.integration.common.OutboundRetryService;
import com.mediaproject.prompthubs.integration.common.crypto.TokenCipher;
import com.mediaproject.prompthubs.integration.common.entity.IntegrationType;
import com.mediaproject.prompthubs.integration.common.entity.SyncRecord;
import com.mediaproject.prompthubs.integration.common.entity.WorkspaceIntegration;
import com.mediaproject.prompthubs.integration.common.event.DocChangedEvent;
import com.mediaproject.prompthubs.integration.common.event.LogCreatedEvent;
import com.mediaproject.prompthubs.integration.common.event.PromptStatusChangedEvent;
import com.mediaproject.prompthubs.integration.common.event.PromptVersionCreatedEvent;
import com.mediaproject.prompthubs.integration.common.repository.SyncRecordRepository;
import com.mediaproject.prompthubs.integration.common.repository.WorkspaceIntegrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Optional;
import java.util.UUID;

/**
 * Pushes internal changes to Notion.
 *
 *  - Each listener catches its own exceptions so the entity context (type + id) is
 *    available for retry queueing.
 *  - {@code forIntegration} no longer swallows exceptions — failures bubble to the
 *    listener-level catch where they are classified.
 *  - Failures classified as {@link ErrorCode#INTEGRATION_OAUTH_FAILED} (HTTP 401)
 *    deactivate the integration and skip the retry queue: the same token will not
 *    succeed on a retry; a human re-OAuth is required.
 *  - All other failures land in {@link OutboundRetryService} for backoff retry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotionOutboundSyncService {

    private final NotionProperties properties;
    private final NotionApiClient apiClient;
    private final NotionPropertyBuilder propertyBuilder;
    private final NotionBlockBuilder blockBuilder;
    private final WorkspaceIntegrationRepository integrationRepository;
    private final SyncRecordRepository syncRepository;
    private final PromptVersionRepository versionRepository;
    private final PromptRepository promptRepository;
    private final PromptLogRepository logRepository;
    private final DocRepository docRepository;
    private final OutboundRetryService retryService;
    private final TokenCipher tokenCipher;

    // ---------- Event listeners ----------

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLogCreated(LogCreatedEvent event) {
        runOrQueue(event.workspaceId(), SyncRecord.EntityType.LOG, event.log().getId(),
                integration -> handleLog(integration, event.log()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDocChanged(DocChangedEvent event) {
        if (event.type() == DocChangedEvent.ChangeType.DELETED) return;
        runOrQueue(event.workspaceId(), SyncRecord.EntityType.DOC, event.doc().getId(),
                integration -> handleDoc(integration, event.doc()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPromptStatusChanged(PromptStatusChangedEvent event) {
        runOrQueue(event.workspaceId(), SyncRecord.EntityType.PROMPT, event.prompt().getId(),
                integration -> handlePrompt(integration, event.prompt(), null));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPromptVersionCreated(PromptVersionCreatedEvent event) {
        runOrQueue(event.workspaceId(), SyncRecord.EntityType.PROMPT, event.prompt().getId(),
                integration -> handlePrompt(integration, event.prompt(), event.version()));
    }

    // ---------- Retry entry point (called by OutboundRetryScheduler) ----------

    /**
     * Re-runs outbound for a single (workspace, type, entityId) target. Throws on failure
     * so the scheduler can mark the retry row appropriately. Always uses claim-first via
     * the same handler methods as the live listeners.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retrySync(UUID workspaceId, SyncRecord.EntityType type, UUID entityId) {
        Optional<WorkspaceIntegration> opt = integrationRepository
                .findByWorkspaceIdAndType(workspaceId, IntegrationType.NOTION);
        if (opt.isEmpty() || !opt.get().isActive()) {
            // Integration was deactivated since we queued — nothing to do.
            return;
        }
        WorkspaceIntegration integration = opt.get();
        switch (type) {
            case LOG -> {
                PromptLog log = logRepository.findById(entityId).orElse(null);
                if (log == null) return;
                handleLog(integration, log);
            }
            case DOC -> {
                Doc doc = docRepository.findById(entityId).orElse(null);
                if (doc == null) return;
                handleDoc(integration, doc);
            }
            case PROMPT -> {
                Prompt prompt = promptRepository.findById(entityId).orElse(null);
                if (prompt == null) return;
                handlePrompt(integration, prompt, null);
            }
        }
    }

    // ---------- Backfill (called right after parent page selection) ----------

    /**
     * Pushes every existing Prompt / Doc / Log of the workspace to Notion.
     * Safe to call multiple times — {@link #claim} dedupes via SyncRecord, so
     * already-synced rows become updates rather than duplicate pages.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int backfillAll(UUID workspaceId) {
        if (!properties.isEnabled()) return 0;
        Optional<WorkspaceIntegration> opt = integrationRepository
                .findByWorkspaceIdAndType(workspaceId, IntegrationType.NOTION);
        if (opt.isEmpty() || !opt.get().isActive()) return 0;
        WorkspaceIntegration integration = opt.get();

        int total = 0;
        total += backfillPage((page) -> promptRepository.findByWorkspaceId(workspaceId, page),
                (prompt) -> handlePrompt(integration, prompt, null), "prompt");
        total += backfillPage((page) -> docRepository.findByWorkspaceId(workspaceId, page),
                (doc) -> handleDoc(integration, doc), "doc");
        total += backfillPage((page) -> logRepository.findByWorkspaceId(workspaceId, page),
                (logEntity) -> handleLog(integration, logEntity), "log");

        log.info("Notion backfill for workspace {} pushed {} items", workspaceId, total);
        return total;
    }

    private <T> int backfillPage(java.util.function.Function<Pageable, Page<T>> fetcher,
                                  java.util.function.Consumer<T> handler,
                                  String kind) {
        int pageNum = 0;
        int pushed = 0;
        Page<T> page;
        do {
            page = fetcher.apply(PageRequest.of(pageNum, 50));
            for (T item : page.getContent()) {
                try {
                    handler.accept(item);
                    pushed++;
                } catch (Exception e) {
                    log.warn("Backfill {} failed for one item: {}", kind, e.getMessage());
                }
            }
            pageNum++;
        } while (page.hasNext());
        return pushed;
    }

    // ---------- Handlers (shared by listeners and retries) ----------

    private void handleLog(WorkspaceIntegration integration, PromptLog logEntity) {
        if (integration.getNotionLogsDbId() == null) return;
        Claim claim = claim(integration, SyncRecord.EntityType.LOG, logEntity.getId());
        if (claim.outcome() != Outcome.WON_CREATE) {
            log.debug("Log {} sync skipped ({})", logEntity.getId(), claim.outcome());
            return;
        }

        ObjectNode props = propertyBuilder.mapper().createObjectNode();
        // Title is just a short label so the DB list view stays readable.
        props.set("프롬프트", propertyBuilder.title(deriveLabel(logEntity.getPromptText())));
        props.set("상태", propertyBuilder.richText(logEntity.getStatus().name()));
        props.set("모델", propertyBuilder.richText(logEntity.getModelUsed()));
        // Keep the summary property compact — long content lives in the body below.
        props.set("결과 요약", propertyBuilder.richText(deriveSummary(logEntity)));

        ObjectNode body = propertyBuilder.wrapPageRequest(integration.getNotionLogsDbId(), props);
        body.set("children", buildLogBody(logEntity));

        JsonNode resp = apiClient.createPage(token(integration), body);
        claim.record().promoteFromPlaceholder(
                resp.path("id").asText(),
                resp.path("last_edited_time").asText(null));
        integration.touchSync();
    }

    private tools.jackson.databind.node.ArrayNode buildLogBody(PromptLog logEntity) {
        var children = blockBuilder.emptyBlockList();
        children.add(blockBuilder.heading2("프롬프트"));
        for (var b : blockBuilder.markdownToBlocks(logEntity.getPromptText())) children.add(b);
        String result = logEntity.getResultBody() != null && !logEntity.getResultBody().isBlank()
                ? logEntity.getResultBody()
                : logEntity.getResultSummary();
        if (result != null && !result.isBlank()) {
            children.add(blockBuilder.heading2("결과"));
            for (var b : blockBuilder.markdownToBlocks(result)) children.add(b);
        }
        return children;
    }

    private static String deriveLabel(String prompt) {
        if (prompt == null || prompt.isBlank()) return "(empty prompt)";
        // First non-blank line, capped — keeps Notion title from wrapping the whole DB.
        String firstLine = prompt.strip().split("\\R", 2)[0].trim();
        if (firstLine.length() > 80) {
            return firstLine.substring(0, 77) + "…";
        }
        return firstLine;
    }

    private static String deriveSummary(PromptLog logEntity) {
        if (logEntity.getResultSummary() != null && !logEntity.getResultSummary().isBlank()) {
            return truncate(logEntity.getResultSummary(), 240);
        }
        if (logEntity.getResultBody() != null && !logEntity.getResultBody().isBlank()) {
            String flat = logEntity.getResultBody().replaceAll("\\s+", " ").trim();
            return truncate(flat, 240);
        }
        return "";
    }

    private void handleDoc(WorkspaceIntegration integration, Doc doc) {
        if (integration.getNotionDocsDbId() == null) return;
        Claim claim = claim(integration, SyncRecord.EntityType.DOC, doc.getId());
        if (claim.outcome() == Outcome.LOST_INFLIGHT_SKIP) {
            log.debug("Doc {} sync skipped (in-flight)", doc.getId());
            return;
        }
        ObjectNode props = propertyBuilder.mapper().createObjectNode();
        props.set("제목", propertyBuilder.title(doc.getTitle()));
        props.set("카테고리", propertyBuilder.select(doc.getCategory()));
        // Docs don't have a long body field on the entity yet — left as null for future enrichment.
        JsonNode resp = pushPage(integration, integration.getNotionDocsDbId(), claim, props, null);
        claim.record().touch(resp.path("last_edited_time").asText(null));
        integration.touchSync();
    }

    private void handlePrompt(WorkspaceIntegration integration, Prompt prompt, PromptVersion version) {
        if (integration.getNotionPromptsDbId() == null) return;
        Claim claim = claim(integration, SyncRecord.EntityType.PROMPT, prompt.getId());
        if (claim.outcome() == Outcome.LOST_INFLIGHT_SKIP) {
            log.debug("Prompt {} sync skipped (in-flight)", prompt.getId());
            return;
        }
        PromptVersion v = version;
        if (v == null && prompt.getCurrentVersionId() != null) {
            v = versionRepository.findById(prompt.getCurrentVersionId()).orElse(null);
        }
        ObjectNode props = propertyBuilder.mapper().createObjectNode();
        props.set("제목", propertyBuilder.title(prompt.getTitle()));
        props.set("상태", propertyBuilder.richText(prompt.getStatus().name()));
        props.set("카테고리", propertyBuilder.richText(prompt.getCategory()));
        if (v != null) {
            // Keep these as 1-line property strings; full criteria can also live in the body if needed.
            props.set("성공 기준", propertyBuilder.richText(truncate(v.getSuccessCriteria(), 240)));
            props.set("검증 방법", propertyBuilder.richText(truncate(v.getValidationMethod(), 240)));
            props.set("버전", propertyBuilder.richText(v.getVersionNumber()));
        }
        // Body blocks only matter on initial create — Notion's PATCH /pages can't replace
        // children, so subsequent updates only touch properties. The body shows the prompt
        // text and the success/validation criteria the team should rely on.
        tools.jackson.databind.node.ArrayNode children = null;
        if (claim.outcome() == Outcome.WON_CREATE) {
            children = buildPromptBody(prompt, v);
        }
        JsonNode resp = pushPage(integration, integration.getNotionPromptsDbId(), claim, props, children);
        claim.record().touch(resp.path("last_edited_time").asText(null));
        integration.touchSync();
    }

    private tools.jackson.databind.node.ArrayNode buildPromptBody(Prompt prompt, PromptVersion v) {
        var children = blockBuilder.emptyBlockList();
        if (v != null && v.getPromptText() != null) {
            children.add(blockBuilder.heading2("프롬프트"));
            for (var b : blockBuilder.markdownToBlocks(v.getPromptText())) children.add(b);
        }
        if (v != null && v.getSuccessCriteria() != null && !v.getSuccessCriteria().isBlank()) {
            children.add(blockBuilder.heading2("성공 기준"));
            for (var b : blockBuilder.markdownToBlocks(v.getSuccessCriteria())) children.add(b);
        }
        if (v != null && v.getValidationMethod() != null && !v.getValidationMethod().isBlank()) {
            children.add(blockBuilder.heading2("검증 방법"));
            for (var b : blockBuilder.markdownToBlocks(v.getValidationMethod())) children.add(b);
        }
        return children;
    }

    /**
     * Wraps {@link #forIntegration} with the failure-classification logic shared by all
     * event listeners. Auth failures deactivate the integration; other exceptions land in
     * the retry queue.
     */
    private void runOrQueue(UUID workspaceId, SyncRecord.EntityType type, UUID entityId,
                            java.util.function.Consumer<WorkspaceIntegration> action) {
        if (!properties.isEnabled()) return;
        Optional<WorkspaceIntegration> opt = integrationRepository
                .findByWorkspaceIdAndType(workspaceId, IntegrationType.NOTION);
        if (opt.isEmpty() || !opt.get().isActive()) return;

        WorkspaceIntegration integration = opt.get();
        try {
            action.accept(integration);
        } catch (BusinessException e) {
            handleFailure(workspaceId, type, entityId, integration, e);
            throw e;  // ensure outer @Transactional rolls back the placeholder claim
        } catch (Exception e) {
            handleFailure(workspaceId, type, entityId, integration,
                    new BusinessException(ErrorCode.EXTERNAL_API_ERROR, e.getMessage()));
            throw new RuntimeException(e);
        }
    }

    private void handleFailure(UUID workspaceId, SyncRecord.EntityType type, UUID entityId,
                               WorkspaceIntegration integration, BusinessException e) {
        if (e.getErrorCode() == ErrorCode.INTEGRATION_OAUTH_FAILED) {
            integration.deactivate();
            log.warn("Notion integration deactivated for workspace {} — re-OAuth required: {}",
                    workspaceId, e.getMessage());
            // Don't queue: same token won't succeed.
            return;
        }
        retryService.queue(workspaceId, type, entityId, IntegrationType.NOTION, e.getMessage());
        log.warn("Notion outbound sync failed for workspace {} ({}={}): {} — queued for retry",
                workspaceId, type, entityId, e.getMessage());
    }

    /**
     * Calls Notion createPage on a fresh claim (with optional body children), or updatePage
     * when an existing real record was found. Notion's PATCH /pages only mutates properties,
     * so children are silently ignored on the update path.
     */
    private JsonNode pushPage(WorkspaceIntegration integration, String databaseId, Claim claim,
                              ObjectNode props, tools.jackson.databind.node.ArrayNode children) {
        if (claim.outcome() == Outcome.WON_CREATE) {
            ObjectNode body = propertyBuilder.wrapPageRequest(databaseId, props);
            if (children != null && children.size() > 0) {
                body.set("children", children);
            }
            JsonNode resp = apiClient.createPage(token(integration), body);
            claim.record().promoteFromPlaceholder(
                    resp.path("id").asText(),
                    resp.path("last_edited_time").asText(null));
            return resp;
        }
        ObjectNode body = propertyBuilder.mapper().createObjectNode();
        body.set("properties", props);
        return apiClient.updatePage(token(integration), claim.record().getExternalId(), body);
    }

    private Claim claim(WorkspaceIntegration integration, SyncRecord.EntityType type, UUID entityId) {
        UUID workspaceId = integration.getWorkspace().getId();
        String placeholder = SyncRecord.PLACEHOLDER_PREFIX + UUID.randomUUID();
        int inserted = syncRepository.tryInsertPlaceholder(
                workspaceId, type.name(), entityId, placeholder, IntegrationType.NOTION.name());
        Optional<SyncRecord> row = syncRepository
                .findByWorkspaceIdAndEntityTypeAndEntityIdAndExternalPlatform(
                        workspaceId, type, entityId, IntegrationType.NOTION);
        if (inserted == 1) {
            return new Claim(Outcome.WON_CREATE, row.orElseThrow(
                    () -> new IllegalStateException("Placeholder vanished after successful insert")));
        }
        if (row.isEmpty() || row.get().isPlaceholder()) {
            return new Claim(Outcome.LOST_INFLIGHT_SKIP, row.orElse(null));
        }
        return new Claim(Outcome.LOST_UPDATE, row.get());
    }

    private String token(WorkspaceIntegration integration) {
        return tokenCipher.decrypt(integration.getAccessTokenEncrypted());
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private enum Outcome { WON_CREATE, LOST_UPDATE, LOST_INFLIGHT_SKIP }

    private record Claim(Outcome outcome, SyncRecord record) {}
}
