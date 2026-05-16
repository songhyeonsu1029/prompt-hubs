package com.mediaproject.prompthubs.domain.prompt.service;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
import com.mediaproject.prompthubs.domain.account.entity.Account;
import com.mediaproject.prompthubs.domain.account.repository.AccountRepository;
import com.mediaproject.prompthubs.domain.prompt.dto.*;
import com.mediaproject.prompthubs.domain.prompt.entity.Prompt;
import com.mediaproject.prompthubs.domain.prompt.entity.PromptVersion;
import com.mediaproject.prompthubs.domain.prompt.repository.PromptRepository;
import com.mediaproject.prompthubs.domain.prompt.repository.PromptVersionRepository;
import com.mediaproject.prompthubs.domain.review.dto.CreateReviewRequest;
import com.mediaproject.prompthubs.domain.review.service.ReviewService;
import com.mediaproject.prompthubs.domain.workspace.entity.Workspace;
import com.mediaproject.prompthubs.domain.workspace.repository.WorkspaceRepository;
import com.mediaproject.prompthubs.domain.workspace.service.PlanLimitService;
import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import com.mediaproject.prompthubs.global.security.WorkspaceContext;
import com.mediaproject.prompthubs.global.security.WorkspaceContextHolder;
import com.mediaproject.prompthubs.integration.common.event.PromptVersionCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VersionService {

    private final PromptRepository promptRepository;
    private final PromptVersionRepository versionRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AccountRepository accountRepository;
    private final PlanLimitService planLimitService;
    private final ReviewService reviewService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public VersionResponse createVersion(UUID promptId, CreateVersionRequest request, UUID accountId) {
        WorkspaceContext context = getWorkspaceContext();

        // Check plan limit for versions
        planLimitService.checkVersionLimit(promptId, context.getWorkspaceId());

        Prompt prompt = promptRepository.findByIdAndWorkspaceId(promptId, context.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Prompt not found"));

        Account author = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        // Generate next version number
        String nextVersionNumber = generateNextVersionNumber(promptId);

        // Create new version
        PromptVersion version = PromptVersion.builder()
                .prompt(prompt)
                .versionNumber(nextVersionNumber)
                .promptText(request.getPromptText())
                .successCriteria(request.getSuccessCriteria())
                .validationMethod(request.getValidationMethod())
                .changeNote(request.getChangeNote())
                .createdBy(author)
                .build();

        PromptVersion savedVersion = versionRepository.save(version);

        // Update current version
        prompt.setCurrentVersion(savedVersion.getId());

        log.info("Version {} created for prompt {} in workspace {}",
                nextVersionNumber, promptId, context.getWorkspaceSlug());

        eventPublisher.publishEvent(new PromptVersionCreatedEvent(
                context.getWorkspaceId(), prompt.getId(), prompt, savedVersion));

        // New version must go through review before it is allowed to overtake
        // the current version on the public list (status → IN_REVIEW inside createReview).
        autoCreateReview(savedVersion.getId(), request.getReviewerId(), accountId);

        return VersionResponse.from(savedVersion);
    }

    private void autoCreateReview(UUID versionId, UUID reviewerId, UUID requesterId) {
        if (reviewerId == null) return;
        CreateReviewRequest req = new CreateReviewRequest();
        req.setPromptVersionId(versionId);
        req.setReviewerId(reviewerId);
        reviewService.createReview(req, requesterId);
    }

    public List<VersionResponse> getVersionHistory(UUID promptId) {
        WorkspaceContext context = getWorkspaceContext();

        // Verify prompt belongs to workspace
        promptRepository.findByIdAndWorkspaceId(promptId, context.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Prompt not found"));

        List<PromptVersion> versions = versionRepository.findByPromptIdOrderByCreatedAtDesc(promptId);

        return versions.stream()
                .map(VersionResponse::from)
                .toList();
    }

    public DiffResponse getDiff(UUID promptId, UUID versionId, UUID compareWithId) {
        WorkspaceContext context = getWorkspaceContext();

        // Verify prompt belongs to workspace
        promptRepository.findByIdAndWorkspaceId(promptId, context.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Prompt not found"));

        PromptVersion sourceVersion = versionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Source version not found"));

        PromptVersion targetVersion = versionRepository.findById(compareWithId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Target version not found"));

        // Verify versions belong to the same prompt
        if (!sourceVersion.getPrompt().getId().equals(promptId) ||
            !targetVersion.getPrompt().getId().equals(promptId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Versions must belong to the same prompt");
        }

        return DiffResponse.builder()
                .sourceVersionId(sourceVersion.getId())
                .sourceVersionNumber(sourceVersion.getVersionNumber())
                .targetVersionId(targetVersion.getId())
                .targetVersionNumber(targetVersion.getVersionNumber())
                .promptTextDiff(computeDiff(sourceVersion.getPromptText(), targetVersion.getPromptText()))
                .successCriteriaDiff(computeDiff(sourceVersion.getSuccessCriteria(), targetVersion.getSuccessCriteria()))
                .validationMethodDiff(computeDiff(sourceVersion.getValidationMethod(), targetVersion.getValidationMethod()))
                .build();
    }

    @Transactional
    public VersionResponse rollback(UUID promptId, RollbackRequest request, UUID accountId) {
        WorkspaceContext context = getWorkspaceContext();

        // Check plan limit for versions
        planLimitService.checkVersionLimit(promptId, context.getWorkspaceId());

        Prompt prompt = promptRepository.findByIdAndWorkspaceId(promptId, context.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Prompt not found"));

        Account author = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        PromptVersion targetVersion = versionRepository.findById(request.getTargetVersionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Target version not found"));

        // Verify target version belongs to this prompt
        if (!targetVersion.getPrompt().getId().equals(promptId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Version does not belong to this prompt");
        }

        // Generate next version number
        String nextVersionNumber = generateNextVersionNumber(promptId);

        // Create new version with rolled back content
        PromptVersion newVersion = PromptVersion.builder()
                .prompt(prompt)
                .versionNumber(nextVersionNumber)
                .promptText(targetVersion.getPromptText())
                .successCriteria(targetVersion.getSuccessCriteria())
                .validationMethod(targetVersion.getValidationMethod())
                .changeNote("Rolled back to v" + targetVersion.getVersionNumber())
                .createdBy(author)
                .build();

        PromptVersion savedVersion = versionRepository.save(newVersion);

        // Update current version
        prompt.setCurrentVersion(savedVersion.getId());

        log.info("Prompt {} rolled back to version {} (new version: {}) in workspace {}",
                promptId, targetVersion.getVersionNumber(), nextVersionNumber, context.getWorkspaceSlug());

        eventPublisher.publishEvent(new PromptVersionCreatedEvent(
                context.getWorkspaceId(), prompt.getId(), prompt, savedVersion));
        return VersionResponse.from(savedVersion);
    }

    private String generateNextVersionNumber(UUID promptId) {
        PromptVersion latestVersion = versionRepository.findLatestByPromptId(promptId).orElse(null);

        if (latestVersion == null) {
            return "1.0";
        }

        String currentVersion = latestVersion.getVersionNumber();
        String[] parts = currentVersion.split("\\.");

        if (parts.length == 2) {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            return major + "." + (minor + 1);
        }

        // Fallback
        long count = versionRepository.countByPromptId(promptId);
        return "1." + count;
    }

    private List<DiffResponse.DiffLine> computeDiff(String original, String revised) {
        List<String> originalLines = Arrays.asList(original.split("\n", -1));
        List<String> revisedLines = Arrays.asList(revised.split("\n", -1));

        Patch<String> patch = DiffUtils.diff(originalLines, revisedLines);
        List<DiffResponse.DiffLine> diffLines = new ArrayList<>();

        int originalLineNum = 0;
        int revisedLineNum = 0;
        int deltaIndex = 0;

        List<AbstractDelta<String>> deltas = patch.getDeltas();

        while (originalLineNum < originalLines.size() || revisedLineNum < revisedLines.size()) {
            AbstractDelta<String> currentDelta = null;

            // Check if there's a delta at current position
            if (deltaIndex < deltas.size()) {
                currentDelta = deltas.get(deltaIndex);
            }

            if (currentDelta != null && currentDelta.getSource().getPosition() == originalLineNum) {
                // Process delta
                if (currentDelta.getType() == DeltaType.DELETE) {
                    for (String line : currentDelta.getSource().getLines()) {
                        diffLines.add(DiffResponse.DiffLine.builder()
                                .lineNumber(originalLineNum + 1)
                                .content(line)
                                .changeType(DiffResponse.DiffLine.ChangeType.DELETED)
                                .build());
                        originalLineNum++;
                    }
                } else if (currentDelta.getType() == DeltaType.INSERT) {
                    for (String line : currentDelta.getTarget().getLines()) {
                        diffLines.add(DiffResponse.DiffLine.builder()
                                .lineNumber(revisedLineNum + 1)
                                .content(line)
                                .changeType(DiffResponse.DiffLine.ChangeType.ADDED)
                                .build());
                        revisedLineNum++;
                    }
                } else if (currentDelta.getType() == DeltaType.CHANGE) {
                    // Deleted lines
                    for (String line : currentDelta.getSource().getLines()) {
                        diffLines.add(DiffResponse.DiffLine.builder()
                                .lineNumber(originalLineNum + 1)
                                .content(line)
                                .changeType(DiffResponse.DiffLine.ChangeType.DELETED)
                                .build());
                        originalLineNum++;
                    }
                    // Added lines
                    for (String line : currentDelta.getTarget().getLines()) {
                        diffLines.add(DiffResponse.DiffLine.builder()
                                .lineNumber(revisedLineNum + 1)
                                .content(line)
                                .changeType(DiffResponse.DiffLine.ChangeType.ADDED)
                                .build());
                        revisedLineNum++;
                    }
                }
                deltaIndex++;
            } else {
                // Unchanged line
                if (originalLineNum < originalLines.size()) {
                    diffLines.add(DiffResponse.DiffLine.builder()
                            .lineNumber(originalLineNum + 1)
                            .content(originalLines.get(originalLineNum))
                            .changeType(DiffResponse.DiffLine.ChangeType.UNCHANGED)
                            .build());
                    originalLineNum++;
                    revisedLineNum++;
                } else {
                    break;
                }
            }
        }

        return diffLines;
    }

    private WorkspaceContext getWorkspaceContext() {
        WorkspaceContext context = WorkspaceContextHolder.getContext();
        if (context == null) {
            throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND, "Workspace context not found");
        }
        return context;
    }
}
