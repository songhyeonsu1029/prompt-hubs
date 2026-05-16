package com.mediaproject.prompthubs.domain.review.service;

import com.mediaproject.prompthubs.domain.account.entity.Account;
import com.mediaproject.prompthubs.domain.account.repository.AccountRepository;
import com.mediaproject.prompthubs.domain.prompt.entity.Prompt;
import com.mediaproject.prompthubs.domain.prompt.entity.PromptVersion;
import com.mediaproject.prompthubs.domain.prompt.repository.PromptVersionRepository;
import com.mediaproject.prompthubs.domain.review.dto.*;
import com.mediaproject.prompthubs.domain.review.entity.PromptReview;
import com.mediaproject.prompthubs.domain.review.entity.ReviewComment;
import com.mediaproject.prompthubs.domain.review.repository.ReviewCommentRepository;
import com.mediaproject.prompthubs.domain.review.repository.ReviewRepository;
import com.mediaproject.prompthubs.domain.workspace.entity.Workspace;
import com.mediaproject.prompthubs.domain.workspace.repository.MemberRepository;
import com.mediaproject.prompthubs.domain.workspace.repository.WorkspaceRepository;
import com.mediaproject.prompthubs.global.common.PageResponse;
import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import com.mediaproject.prompthubs.global.security.WorkspaceContext;
import com.mediaproject.prompthubs.global.security.WorkspaceContextHolder;
import com.mediaproject.prompthubs.integration.common.event.PromptStatusChangedEvent;
import com.mediaproject.prompthubs.integration.common.event.ReviewCompletedEvent;
import com.mediaproject.prompthubs.integration.common.event.ReviewRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewCommentRepository commentRepository;
    private final PromptVersionRepository versionRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AccountRepository accountRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ReviewResponse createReview(CreateReviewRequest request, UUID requesterId) {
        WorkspaceContext context = getWorkspaceContext();

        Workspace workspace = workspaceRepository.findById(context.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));

        Account requester = accountRepository.findById(requesterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        Account reviewer = accountRepository.findById(request.getReviewerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "Reviewer not found"));

        // Verify reviewer is a workspace member
        if (!memberRepository.existsByAccountIdAndWorkspaceId(request.getReviewerId(), context.getWorkspaceId())) {
            throw new BusinessException(ErrorCode.NOT_WORKSPACE_MEMBER, "Reviewer is not a workspace member");
        }

        PromptVersion version = versionRepository.findById(request.getPromptVersionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Version not found"));

        // Update prompt status to IN_REVIEW
        Prompt prompt = version.getPrompt();
        Prompt.Status oldStatus = prompt.getStatus();
        prompt.updateStatus(Prompt.Status.IN_REVIEW);

        // Create review
        PromptReview review = PromptReview.builder()
                .workspace(workspace)
                .version(version)
                .requester(requester)
                .reviewer(reviewer)
                .build();

        PromptReview savedReview = reviewRepository.save(review);

        log.info("Review created: {} for prompt {} in workspace {}",
                savedReview.getId(), prompt.getId(), context.getWorkspaceSlug());

        eventPublisher.publishEvent(new ReviewRequestedEvent(workspace.getId(), savedReview.getId(), savedReview));
        if (oldStatus != Prompt.Status.IN_REVIEW) {
            eventPublisher.publishEvent(new PromptStatusChangedEvent(
                    workspace.getId(), prompt.getId(), prompt, oldStatus, Prompt.Status.IN_REVIEW));
        }
        return ReviewResponse.from(savedReview, 0);
    }

    public PageResponse<ReviewResponse> getReviews(PromptReview.Status status, UUID reviewerId,
            UUID requesterId, UUID promptId, Pageable pageable) {
        WorkspaceContext context = getWorkspaceContext();
        Page<PromptReview> reviews;

        if (status != null) {
            reviews = reviewRepository.findByWorkspaceIdAndStatus(context.getWorkspaceId(), status, pageable);
        } else if (reviewerId != null) {
            reviews = reviewRepository.findByWorkspaceIdAndReviewerId(context.getWorkspaceId(), reviewerId, pageable);
        } else if (requesterId != null) {
            reviews = reviewRepository.findByWorkspaceIdAndRequesterId(context.getWorkspaceId(), requesterId, pageable);
        } else if (promptId != null) {
            reviews = reviewRepository.findByWorkspaceIdAndPromptId(context.getWorkspaceId(), promptId, pageable);
        } else {
            reviews = reviewRepository.findByWorkspaceId(context.getWorkspaceId(), pageable);
        }

        Page<ReviewResponse> responsePage = reviews.map(r -> {
            long unresolved = commentRepository.countUnresolvedByReviewId(r.getId());
            return ReviewResponse.from(r, unresolved);
        });

        return PageResponse.of(responsePage);
    }

    public ReviewResponse getReview(UUID reviewId) {
        WorkspaceContext context = getWorkspaceContext();

        PromptReview review = reviewRepository.findByIdAndWorkspaceId(reviewId, context.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Review not found"));

        long unresolved = commentRepository.countUnresolvedByReviewId(reviewId);
        return ReviewResponse.from(review, unresolved);
    }

    /**
     * Mutates the prompt + version pair attached to a PENDING review. Used by the reviewer
     * or requester to polish the draft before approval, instead of bouncing the review
     * back-and-forth via comments. Only PENDING reviews are editable.
     */
    @Transactional
    public ReviewResponse updateDraft(UUID reviewId, UpdateReviewDraftRequest request, UUID accountId) {
        WorkspaceContext context = getWorkspaceContext();

        PromptReview review = reviewRepository.findByIdAndWorkspaceId(reviewId, context.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Review not found"));

        if (review.getStatus() != PromptReview.Status.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Only PENDING reviews can be edited");
        }
        if (!review.getRequester().getId().equals(accountId)) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION,
                    "Only the requester can edit this draft. Reviewers should leave comments instead.");
        }

        PromptVersion version = review.getVersion();
        Prompt prompt = version.getPrompt();

        prompt.update(request.getTitle(), request.getCategory(), request.getTags());
        version.updateDraftBody(request.getPromptText(),
                request.getSuccessCriteria(),
                request.getValidationMethod());

        long unresolved = commentRepository.countUnresolvedByReviewId(reviewId);
        return ReviewResponse.from(review, unresolved);
    }

    @Transactional
    public ReviewResponse updateReviewStatus(UUID reviewId, UpdateReviewRequest request, UUID accountId) {
        WorkspaceContext context = getWorkspaceContext();

        PromptReview review = reviewRepository.findByIdAndWorkspaceId(reviewId, context.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Review not found"));

        // Only the reviewer can approve/reject
        if (!review.getReviewer().getId().equals(accountId)) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION, "Only the reviewer can update status");
        }

        // Only PENDING reviews can be updated
        if (review.getStatus() != PromptReview.Status.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Review has already been processed");
        }

        Prompt prompt = review.getVersion().getPrompt();
        Prompt.Status oldStatus = prompt.getStatus();
        Prompt.Status newStatus = oldStatus;

        if (request.getStatus() == PromptReview.Status.APPROVED) {
            review.approve();
            prompt.updateStatus(Prompt.Status.APPROVED);
            newStatus = Prompt.Status.APPROVED;
            log.info("Review {} approved for prompt {}", reviewId, prompt.getId());
        } else if (request.getStatus() == PromptReview.Status.REJECTED) {
            review.reject();
            prompt.updateStatus(Prompt.Status.DRAFT);
            newStatus = Prompt.Status.DRAFT;
            log.info("Review {} rejected for prompt {}", reviewId, prompt.getId());
        }

        eventPublisher.publishEvent(new ReviewCompletedEvent(
                context.getWorkspaceId(), review.getId(), review, review.getStatus()));
        if (oldStatus != newStatus) {
            eventPublisher.publishEvent(new PromptStatusChangedEvent(
                    context.getWorkspaceId(), prompt.getId(), prompt, oldStatus, newStatus));
        }
        return ReviewResponse.from(review);
    }

    @Transactional
    public CommentResponse createComment(UUID reviewId, CreateCommentRequest request, UUID authorId) {
        WorkspaceContext context = getWorkspaceContext();

        PromptReview review = reviewRepository.findByIdAndWorkspaceId(reviewId, context.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Review not found"));

        Account author = accountRepository.findById(authorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        ReviewComment comment = ReviewComment.builder()
                .review(review)
                .author(author)
                .lineStart(request.getLineStart())
                .lineEnd(request.getLineEnd())
                .content(request.getContent())
                .build();

        ReviewComment savedComment = commentRepository.save(comment);

        log.info("Comment created on review {} at lines {}-{}",
                reviewId, request.getLineStart(), request.getLineEnd());

        return CommentResponse.from(savedComment);
    }

    public List<CommentResponse> getComments(UUID reviewId) {
        WorkspaceContext context = getWorkspaceContext();

        // Verify review belongs to workspace
        reviewRepository.findByIdAndWorkspaceId(reviewId, context.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Review not found"));

        List<ReviewComment> comments = commentRepository.findByReviewIdOrderByLineStartAsc(reviewId);

        return comments.stream()
                .map(CommentResponse::from)
                .toList();
    }

    @Transactional
    public CommentResponse resolveComment(UUID reviewId, UUID commentId) {
        WorkspaceContext context = getWorkspaceContext();

        // Verify review belongs to workspace
        reviewRepository.findByIdAndWorkspaceId(reviewId, context.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Review not found"));

        ReviewComment comment = commentRepository.findByIdAndReviewId(commentId, reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Comment not found"));

        comment.resolve();

        log.info("Comment {} resolved on review {}", commentId, reviewId);

        return CommentResponse.from(comment);
    }

    private WorkspaceContext getWorkspaceContext() {
        WorkspaceContext context = WorkspaceContextHolder.getContext();
        if (context == null) {
            throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND, "Workspace context not found");
        }
        return context;
    }
}