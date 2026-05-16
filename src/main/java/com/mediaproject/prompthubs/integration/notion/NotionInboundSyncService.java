package com.mediaproject.prompthubs.integration.notion;

import com.mediaproject.prompthubs.domain.account.entity.Account;
import com.mediaproject.prompthubs.domain.prompt.entity.Prompt;
import com.mediaproject.prompthubs.domain.prompt.entity.PromptVersion;
import com.mediaproject.prompthubs.domain.prompt.repository.PromptRepository;
import com.mediaproject.prompthubs.domain.prompt.repository.PromptVersionRepository;
import com.mediaproject.prompthubs.domain.workspace.entity.Member;
import com.mediaproject.prompthubs.domain.workspace.entity.Workspace;
import com.mediaproject.prompthubs.domain.workspace.repository.MemberRepository;
import com.mediaproject.prompthubs.integration.common.crypto.TokenCipher;
import com.mediaproject.prompthubs.integration.common.entity.IntegrationType;
import com.mediaproject.prompthubs.integration.common.entity.SyncRecord;
import com.mediaproject.prompthubs.integration.common.entity.WorkspaceIntegration;
import com.mediaproject.prompthubs.integration.common.event.PrePromptingViolationEvent;
import com.mediaproject.prompthubs.integration.common.repository.SyncRecordRepository;
import com.mediaproject.prompthubs.integration.common.repository.WorkspaceIntegrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Periodic poll of Notion's Prompts DB. Drives three flows:
 *
 *  1. Pre-prompting validation: pages flipped to "In Review" with empty 성공 기준 / 검증 방법
 *     are bounced back to Draft and a violation event is published.
 *  2. Inbound import: pages with no SyncRecord are turned into a fresh Prompt + initial
 *     PromptVersion. Entities are written directly so no outbound event is published —
 *     otherwise the outbound listener would round-trip a duplicate Notion page.
 *  3. Body-change sync: pages whose last_edited_time has advanced past lastSyncedHash
 *     get a new PromptVersion if their block text actually differs from the current
 *     version. Same direct-write principle applies.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotionInboundSyncService {

    private static final List<String> SECTION_BLOCK_TYPES = List.of(
            "paragraph", "heading_1", "heading_2", "heading_3",
            "bulleted_list_item", "numbered_list_item", "quote", "code");

    private final NotionProperties properties;
    private final NotionApiClient apiClient;
    private final NotionPropertyBuilder propertyBuilder;
    private final WorkspaceIntegrationRepository integrationRepository;
    private final SyncRecordRepository syncRepository;
    private final PromptRepository promptRepository;
    private final PromptVersionRepository versionRepository;
    private final MemberRepository memberRepository;
    private final TokenCipher tokenCipher;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void syncAll() {
        if (!properties.isEnabled()) return;
        for (WorkspaceIntegration integration :
                integrationRepository.findByTypeAndActiveTrue(IntegrationType.NOTION)) {
            try {
                syncWorkspace(integration);
            } catch (Exception e) {
                log.warn("Notion inbound sync failed for workspace {}: {}",
                        integration.getWorkspace().getSlug(), e.getMessage());
            }
        }
    }

    private void syncWorkspace(WorkspaceIntegration integration) {
        if (integration.getNotionPromptsDbId() == null) return;
        String token = tokenCipher.decrypt(integration.getAccessTokenEncrypted());

        JsonNode response = apiClient.queryDatabase(token, integration.getNotionPromptsDbId(), null);
        if (response == null) return;

        for (JsonNode page : response.path("results")) {
            try {
                processPage(integration, token, page);
            } catch (Exception e) {
                log.warn("Failed to process Notion page {}: {}",
                        page.path("id").asText(), e.getMessage());
            }
        }
        integration.touchSync();
    }

    private void processPage(WorkspaceIntegration integration, String token, JsonNode page) {
        String pageId = page.path("id").asText();
        String lastEdited = page.path("last_edited_time").asText(null);
        JsonNode props = page.path("properties");

        String status = firstRichText(props.path("상태").path("rich_text"));
        String title = firstTitle(props.path("제목").path("title"));
        String successCriteria = firstRichText(props.path("성공 기준").path("rich_text"));
        String validation = firstRichText(props.path("검증 방법").path("rich_text"));

        // (1) Pre-prompting validation — runs regardless of import state.
        if (isInReview(status) && (isBlank(successCriteria) || isBlank(validation))) {
            bounceToDraft(integration, token, pageId, title);
            return;
        }

        Optional<SyncRecord> existing = syncRepository
                .findByExternalPlatformAndExternalId(IntegrationType.NOTION, pageId);

        if (existing.isEmpty()) {
            // (2) New page in Notion — import as a fresh Prompt.
            importNewPrompt(integration, token, page, props, lastEdited, title, successCriteria, validation, status);
            return;
        }

        SyncRecord record = existing.get();
        // Skip rows that are claim-first placeholders — outbound is mid-flight.
        if (record.isPlaceholder()) {
            return;
        }
        // Only PROMPT records flow through this DB; defensive guard.
        if (record.getEntityType() != SyncRecord.EntityType.PROMPT) {
            record.touch(lastEdited);
            return;
        }

        // (3) Known prompt — only import a new version if Notion has been edited
        // since our last sync.
        boolean changedSinceLastSync = lastEdited != null
                && !Objects.equals(lastEdited, record.getLastSyncedHash());
        if (changedSinceLastSync) {
            importNewVersionIfBodyChanged(integration, token, pageId, page, props, lastEdited,
                    successCriteria, validation, record);
        } else {
            record.touch(lastEdited);
        }
    }

    private void importNewPrompt(WorkspaceIntegration integration, String token, JsonNode page, JsonNode props,
                                 String lastEdited, String title, String successCriteria, String validation,
                                 String status) {
        if (isBlank(title)) {
            log.debug("Skipping Notion page {} import: missing title", page.path("id").asText());
            return;
        }
        if (isBlank(successCriteria) || isBlank(validation)) {
            // Pre-prompting requires both. We don't import unfinished pages — let the user fill
            // them out first; the next poll will pick the page up.
            return;
        }

        Account author = resolveOwnerAccount(integration);
        if (author == null) {
            log.warn("Cannot import Notion page {}: workspace {} has no owner",
                    page.path("id").asText(), integration.getWorkspace().getSlug());
            return;
        }

        String pageId = page.path("id").asText();
        String body = fetchPromptText(token, pageId);
        if (isBlank(body)) {
            log.debug("Skipping Notion page {} import: empty body", pageId);
            return;
        }

        Workspace workspace = integration.getWorkspace();
        Prompt prompt = Prompt.builder()
                .workspace(workspace)
                .author(author)
                .title(title)
                .category(firstRichText(props.path("카테고리").path("rich_text")))
                .tags(parseTags(firstRichText(props.path("태그").path("rich_text"))))
                .status(parseStatus(status))
                .build();
        Prompt savedPrompt = promptRepository.save(prompt);

        String versionNumber = firstRichText(props.path("버전").path("rich_text"));
        PromptVersion version = PromptVersion.builder()
                .prompt(savedPrompt)
                .versionNumber(isBlank(versionNumber) ? "1.0" : versionNumber)
                .promptText(body)
                .successCriteria(successCriteria)
                .validationMethod(validation)
                .changeNote("Imported from Notion")
                .createdBy(author)
                .build();
        PromptVersion savedVersion = versionRepository.save(version);
        savedPrompt.setCurrentVersion(savedVersion.getId());

        SyncRecord record = SyncRecord.builder()
                .workspace(workspace)
                .entityType(SyncRecord.EntityType.PROMPT)
                .entityId(savedPrompt.getId())
                .externalId(pageId)
                .externalPlatform(IntegrationType.NOTION)
                .lastSyncedAt(LocalDateTime.now())
                .lastSyncedHash(lastEdited)
                .build();
        syncRepository.save(record);

        log.info("Imported Notion page {} as new prompt {} in workspace {}",
                pageId, savedPrompt.getId(), workspace.getSlug());
    }

    private void importNewVersionIfBodyChanged(WorkspaceIntegration integration, String token, String pageId,
                                               JsonNode page, JsonNode props, String lastEdited,
                                               String successCriteria, String validation, SyncRecord record) {
        Prompt prompt = promptRepository.findById(record.getEntityId()).orElse(null);
        if (prompt == null) {
            log.warn("SyncRecord {} points to missing prompt {}", record.getId(), record.getEntityId());
            return;
        }

        String body = fetchPromptText(token, pageId);
        if (isBlank(body)) {
            record.touch(lastEdited);
            return;
        }

        PromptVersion current = versionRepository.findLatestByPromptId(prompt.getId()).orElse(null);
        if (current != null
                && Objects.equals(current.getPromptText(), body)
                && Objects.equals(current.getSuccessCriteria(), successCriteria)
                && Objects.equals(current.getValidationMethod(), validation)) {
            // Edit was metadata-only (or whitespace) — bump watermark, no new version.
            record.touch(lastEdited);
            return;
        }

        Account author = resolveOwnerAccount(integration);
        if (author == null) return;

        String nextVersion = bumpVersionNumber(current == null ? null : current.getVersionNumber());
        PromptVersion newVersion = PromptVersion.builder()
                .prompt(prompt)
                .versionNumber(nextVersion)
                .promptText(body)
                .successCriteria(isBlank(successCriteria) && current != null ? current.getSuccessCriteria() : successCriteria)
                .validationMethod(isBlank(validation) && current != null ? current.getValidationMethod() : validation)
                .changeNote("Imported edit from Notion")
                .createdBy(author)
                .build();
        PromptVersion savedVersion = versionRepository.save(newVersion);
        prompt.setCurrentVersion(savedVersion.getId());

        // Optional: also keep title/category/tags in sync from Notion.
        String title = firstTitle(props.path("제목").path("title"));
        String category = firstRichText(props.path("카테고리").path("rich_text"));
        List<String> tags = parseTags(firstRichText(props.path("태그").path("rich_text")));
        prompt.update(title, category, tags);

        record.touch(lastEdited);
        log.info("Imported new version {} for prompt {} from Notion page {}",
                nextVersion, prompt.getId(), pageId);
    }

    private void bounceToDraft(WorkspaceIntegration integration, String token, String pageId, String title) {
        ObjectNode props = propertyBuilder.mapper().createObjectNode();
        props.set("상태", propertyBuilder.richText("Draft"));
        ObjectNode body = propertyBuilder.mapper().createObjectNode();
        body.set("properties", props);

        try {
            apiClient.updatePage(token, pageId, body);
        } catch (Exception e) {
            log.warn("Failed to bounce Notion page {} back to Draft: {}", pageId, e.getMessage());
        }

        UUID syntheticPromptId = syncRepository
                .findByExternalPlatformAndExternalId(IntegrationType.NOTION, pageId)
                .map(SyncRecord::getEntityId)
                .orElse(null);

        eventPublisher.publishEvent(new PrePromptingViolationEvent(
                integration.getWorkspace().getId(),
                syntheticPromptId,
                title == null ? "(untitled)" : title,
                "성공 기준 또는 검증 방법이 비어 있습니다",
                "https://www.notion.so/" + pageId.replace("-", "")
        ));
    }

    private String fetchPromptText(String token, String pageId) {
        try {
            JsonNode blocks = apiClient.getPageBlocks(token, pageId);
            return extractBlockText(blocks);
        } catch (Exception e) {
            log.warn("Could not fetch blocks for page {}: {}", pageId, e.getMessage());
            return null;
        }
    }

    private String extractBlockText(JsonNode blocksResponse) {
        if (blocksResponse == null) return null;
        JsonNode results = blocksResponse.path("results");
        if (!results.isArray()) return null;

        StringBuilder sb = new StringBuilder();
        for (JsonNode block : results) {
            String type = block.path("type").asText();
            if (!SECTION_BLOCK_TYPES.contains(type)) continue;
            JsonNode richText = block.path(type).path("rich_text");
            if (!richText.isArray()) continue;
            String line = StreamSupport.stream(richText.spliterator(), false)
                    .map(rt -> rt.path("plain_text").asText(""))
                    .collect(Collectors.joining());
            if (!line.isEmpty()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private Account resolveOwnerAccount(WorkspaceIntegration integration) {
        return memberRepository.findOwnerByWorkspaceId(integration.getWorkspace().getId())
                .map(Member::getAccount)
                .orElse(null);
    }

    private static Prompt.Status parseStatus(String s) {
        if (s == null) return Prompt.Status.DRAFT;
        String norm = s.trim().toUpperCase().replace(' ', '_');
        return switch (norm) {
            case "IN_REVIEW", "REVIEW", "IN-REVIEW" -> Prompt.Status.IN_REVIEW;
            case "APPROVED", "APPROVE", "APPROVE_" -> Prompt.Status.APPROVED;
            default -> Prompt.Status.DRAFT;
        };
    }

    private static List<String> parseTags(String rawText) {
        if (rawText == null || rawText.isBlank()) return List.of();
        return Arrays.stream(rawText.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String bumpVersionNumber(String current) {
        if (current == null || current.isBlank()) return "1.0";
        String[] parts = current.split("\\.");
        if (parts.length == 2) {
            try {
                int major = Integer.parseInt(parts[0]);
                int minor = Integer.parseInt(parts[1]);
                return major + "." + (minor + 1);
            } catch (NumberFormatException ignored) { /* fall through */ }
        }
        return current + ".1";
    }

    private static boolean isInReview(String status) {
        return "In Review".equalsIgnoreCase(status) || "IN_REVIEW".equalsIgnoreCase(status);
    }

    private static String firstTitle(JsonNode arr) {
        if (arr == null || !arr.isArray() || arr.isEmpty()) return null;
        return arr.path(0).path("plain_text").asText(null);
    }

    private static String firstRichText(JsonNode arr) {
        if (arr == null || !arr.isArray() || arr.isEmpty()) return null;
        return arr.path(0).path("plain_text").asText(null);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
