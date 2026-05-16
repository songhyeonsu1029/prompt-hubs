package com.mediaproject.prompthubs.domain.prompt.service;

import com.mediaproject.prompthubs.domain.account.entity.Account;
import com.mediaproject.prompthubs.domain.account.repository.AccountRepository;
import com.mediaproject.prompthubs.domain.doc.entity.Doc;
import com.mediaproject.prompthubs.domain.doc.repository.DocRepository;
import com.mediaproject.prompthubs.domain.log.entity.PromptLog;
import com.mediaproject.prompthubs.domain.log.service.LogService;
import com.mediaproject.prompthubs.domain.prompt.dto.*;
import com.mediaproject.prompthubs.domain.prompt.entity.Prompt;
import com.mediaproject.prompthubs.domain.prompt.entity.PromptDocLink;
import com.mediaproject.prompthubs.domain.prompt.entity.PromptVersion;
import com.mediaproject.prompthubs.domain.prompt.repository.PromptDocLinkRepository;
import com.mediaproject.prompthubs.domain.prompt.repository.PromptRepository;
import com.mediaproject.prompthubs.domain.prompt.repository.PromptVersionRepository;
import com.mediaproject.prompthubs.domain.review.dto.CreateReviewRequest;
import com.mediaproject.prompthubs.domain.review.service.ReviewService;
import com.mediaproject.prompthubs.domain.workspace.entity.Workspace;
import com.mediaproject.prompthubs.domain.workspace.repository.WorkspaceRepository;
import com.mediaproject.prompthubs.domain.workspace.service.PlanLimitService;
import com.mediaproject.prompthubs.global.common.PageResponse;
import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import com.mediaproject.prompthubs.global.security.WorkspaceContext;
import com.mediaproject.prompthubs.global.security.WorkspaceContextHolder;
import com.mediaproject.prompthubs.integration.common.event.PromptVersionCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromptService {

    private final PromptRepository promptRepository;
    private final PromptVersionRepository versionRepository;
    private final PromptDocLinkRepository docLinkRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AccountRepository accountRepository;
    private final DocRepository docRepository;
    private final LogService logService;
    private final PlanLimitService planLimitService;
    private final ReviewService reviewService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PromptResponse create(CreatePromptRequest request, UUID accountId) {
        WorkspaceContext context = getWorkspaceContext();

        // Check plan limit
        planLimitService.checkPromptLimit(context.getWorkspaceId());

        Workspace workspace = workspaceRepository.findById(context.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));

        Account author = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        // Create prompt
        Prompt prompt = Prompt.builder()
                .workspace(workspace)
                .author(author)
                .title(request.getTitle())
                .category(request.getCategory())
                .tags(request.getTags() != null ? request.getTags() : new ArrayList<>())
                .status(Prompt.Status.DRAFT)
                .build();

        Prompt savedPrompt = promptRepository.save(prompt);

        // Create initial version (1.0)
        PromptVersion version = PromptVersion.builder()
                .prompt(savedPrompt)
                .versionNumber("1.0")
                .promptText(request.getPromptText())
                .successCriteria(request.getSuccessCriteria())
                .validationMethod(request.getValidationMethod())
                .changeNote(request.getChangeNote() != null ? request.getChangeNote() : "Initial version")
                .createdBy(author)
                .build();

        PromptVersion savedVersion = versionRepository.save(version);

        // Set current version
        savedPrompt.setCurrentVersion(savedVersion.getId());

        // Link docs
        List<PromptResponse.LinkedDocInfo> linkedDocs = saveDocLinks(savedPrompt, request.getLinkedDocIds(), context.getWorkspaceId());

        log.info("Prompt created: {} in workspace {}", savedPrompt.getId(), context.getWorkspaceSlug());

        // On creation, publish only VersionCreatedEvent — the status "change" from null→DRAFT
        // is the creation itself, not a transition. Publishing both caused duplicate Notion pages
        // because outbound listeners ran concurrently and each saw an empty sync_records table.
        eventPublisher.publishEvent(new PromptVersionCreatedEvent(
                workspace.getId(), savedPrompt.getId(), savedPrompt, savedVersion));

        // No direct path to APPROVED — every new prompt enters the review queue first.
        autoCreateReview(savedVersion.getId(), request.getReviewerId(), accountId);

        PromptResponse response = PromptResponse.from(savedPrompt, savedVersion);
        response.setLinkedDocs(linkedDocs);
        return response;
    }

    /**
     * Files a PENDING review against the given version with the chosen reviewer. The newly
     * created prompt/version is left in DRAFT (per service contract) and review creation
     * transitions it to IN_REVIEW — same path as a manually filed review.
     */
    private void autoCreateReview(UUID versionId, UUID reviewerId, UUID requesterId) {
        if (reviewerId == null) return;
        CreateReviewRequest req = new CreateReviewRequest();
        req.setPromptVersionId(versionId);
        req.setReviewerId(reviewerId);
        reviewService.createReview(req, requesterId);
    }

    public PageResponse<PromptListResponse> getPrompts(Prompt.Status status, String category, String keyword, Pageable pageable) {
        WorkspaceContext context = getWorkspaceContext();
        Page<Prompt> prompts;

        if (status != null) {
            prompts = promptRepository.findByWorkspaceIdAndStatus(context.getWorkspaceId(), status, pageable);
        } else if (category != null && !category.isBlank()) {
            prompts = promptRepository.findByWorkspaceIdAndCategory(context.getWorkspaceId(), category, pageable);
        } else if (keyword != null && !keyword.isBlank()) {
            prompts = promptRepository.findByWorkspaceIdAndKeyword(context.getWorkspaceId(), keyword, pageable);
        } else {
            prompts = promptRepository.findByWorkspaceId(context.getWorkspaceId(), pageable);
        }

        Page<PromptListResponse> responsePage = prompts.map(PromptListResponse::from);
        return PageResponse.of(responsePage);
    }

    public PromptResponse getPrompt(UUID promptId) {
        WorkspaceContext context = getWorkspaceContext();

        Prompt prompt = promptRepository.findByIdAndWorkspaceId(promptId, context.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Prompt not found"));

        PromptVersion currentVersion = null;
        if (prompt.getCurrentVersionId() != null) {
            currentVersion = versionRepository.findById(prompt.getCurrentVersionId()).orElse(null);
        }

        PromptResponse response = PromptResponse.from(prompt, currentVersion);
        response.setLinkedDocs(getLinkedDocs(promptId));
        return response;
    }

    @Transactional
    public PromptResponse updateLinkedDocs(UUID promptId, List<UUID> docIds) {
        WorkspaceContext context = getWorkspaceContext();

        Prompt prompt = promptRepository.findByIdAndWorkspaceId(promptId, context.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Prompt not found"));

        // Clear existing links and re-create
        docLinkRepository.deleteByPromptId(promptId);
        List<PromptResponse.LinkedDocInfo> linkedDocs = saveDocLinks(prompt, docIds, context.getWorkspaceId());

        PromptVersion currentVersion = null;
        if (prompt.getCurrentVersionId() != null) {
            currentVersion = versionRepository.findById(prompt.getCurrentVersionId()).orElse(null);
        }

        PromptResponse response = PromptResponse.from(prompt, currentVersion);
        response.setLinkedDocs(linkedDocs);
        return response;
    }

    @Transactional
    public PromptResponse promoteLog(UUID logId, PromoteLogRequest request, UUID accountId) {
        WorkspaceContext context = getWorkspaceContext();

        // Check plan limit
        planLimitService.checkPromptLimit(context.getWorkspaceId());

        // Get the log to promote
        PromptLog promptLog = logService.getLogEntity(logId, context.getWorkspaceId());

        Workspace workspace = workspaceRepository.findById(context.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));

        Account author = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        // Use log's promptText if not overridden
        String promptText = request.getPromptText() != null ? request.getPromptText() : promptLog.getPromptText();

        // Create prompt with sourceLogId
        Prompt prompt = Prompt.builder()
                .workspace(workspace)
                .author(author)
                .title(request.getTitle())
                .category(request.getCategory())
                .tags(request.getTags() != null ? request.getTags() : new ArrayList<>())
                .status(Prompt.Status.DRAFT)
                .sourceLogId(logId)
                .build();

        Prompt savedPrompt = promptRepository.save(prompt);

        // Create initial version
        PromptVersion version = PromptVersion.builder()
                .prompt(savedPrompt)
                .versionNumber("1.0")
                .promptText(promptText)
                .successCriteria(request.getSuccessCriteria())
                .validationMethod(request.getValidationMethod())
                .changeNote("Promoted from log")
                .createdBy(author)
                .build();

        PromptVersion savedVersion = versionRepository.save(version);

        // Set current version
        savedPrompt.setCurrentVersion(savedVersion.getId());

        log.info("Log {} promoted to prompt {} in workspace {}", logId, savedPrompt.getId(), context.getWorkspaceSlug());

        eventPublisher.publishEvent(new PromptVersionCreatedEvent(
                workspace.getId(), savedPrompt.getId(), savedPrompt, savedVersion));

        // Same single-path rule as direct create: promoted log → DRAFT prompt → PENDING review.
        autoCreateReview(savedVersion.getId(), request.getReviewerId(), accountId);

        return PromptResponse.from(savedPrompt, savedVersion);
    }

    private List<PromptResponse.LinkedDocInfo> saveDocLinks(Prompt prompt, List<UUID> docIds, UUID workspaceId) {
        if (docIds == null || docIds.isEmpty()) {
            return List.of();
        }

        List<PromptResponse.LinkedDocInfo> result = new ArrayList<>();
        for (UUID docId : docIds) {
            Doc doc = docRepository.findByIdAndWorkspaceId(docId, workspaceId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND,
                            "Doc not found: " + docId));

            PromptDocLink link = PromptDocLink.builder()
                    .prompt(prompt)
                    .doc(doc)
                    .build();
            docLinkRepository.save(link);

            result.add(PromptResponse.LinkedDocInfo.builder()
                    .id(doc.getId())
                    .title(doc.getTitle())
                    .category(doc.getCategory())
                    .build());
        }
        return result;
    }

    private List<PromptResponse.LinkedDocInfo> getLinkedDocs(UUID promptId) {
        return docLinkRepository.findByPromptIdWithDoc(promptId).stream()
                .map(link -> PromptResponse.LinkedDocInfo.builder()
                        .id(link.getDoc().getId())
                        .title(link.getDoc().getTitle())
                        .category(link.getDoc().getCategory())
                        .build())
                .toList();
    }

    private WorkspaceContext getWorkspaceContext() {
        WorkspaceContext context = WorkspaceContextHolder.getContext();
        if (context == null) {
            throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND, "Workspace context not found");
        }
        return context;
    }
}