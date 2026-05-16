package com.mediaproject.prompthubs.domain.review.controller;

import com.mediaproject.prompthubs.domain.review.dto.*;
import com.mediaproject.prompthubs.domain.review.entity.PromptReview;
import com.mediaproject.prompthubs.domain.review.service.ReviewService;
import com.mediaproject.prompthubs.global.common.PageResponse;
import com.mediaproject.prompthubs.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Review", description = "Prompt review API")
@RestController
@RequestMapping("/api/v1/w/{slug}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "Create review", description = "Request a review for a prompt version")
    @PostMapping
    public ResponseEntity<ReviewResponse> createReview(
            @PathVariable String slug,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateReviewRequest request) {
        ReviewResponse response = reviewService.createReview(request, userDetails.getAccountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get reviews", description = "Get list of reviews with filters")
    @GetMapping
    public ResponseEntity<PageResponse<ReviewResponse>> getReviews(
            @PathVariable String slug,
            @RequestParam(required = false) PromptReview.Status status,
            @RequestParam(required = false) UUID reviewerId,
            @RequestParam(required = false) UUID requesterId,
            @RequestParam(required = false) UUID promptId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<ReviewResponse> response = reviewService.getReviews(status, reviewerId, requesterId, promptId, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get review", description = "Get a specific review")
    @GetMapping("/{reviewId}")
    public ResponseEntity<ReviewResponse> getReview(
            @PathVariable String slug,
            @PathVariable UUID reviewId) {
        ReviewResponse response = reviewService.getReview(reviewId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update review status", description = "Approve or reject a review")
    @PatchMapping("/{reviewId}")
    public ResponseEntity<ReviewResponse> updateReviewStatus(
            @PathVariable String slug,
            @PathVariable UUID reviewId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateReviewRequest request) {
        ReviewResponse response = reviewService.updateReviewStatus(reviewId, request, userDetails.getAccountId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Edit review draft",
            description = "Polish the attached prompt + version body while review is PENDING. Allowed for requester or reviewer.")
    @PatchMapping("/{reviewId}/draft")
    public ResponseEntity<ReviewResponse> updateDraft(
            @PathVariable String slug,
            @PathVariable UUID reviewId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateReviewDraftRequest request) {
        ReviewResponse response = reviewService.updateDraft(reviewId, request, userDetails.getAccountId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Create comment", description = "Add an inline comment to a review")
    @PostMapping("/{reviewId}/comments")
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable String slug,
            @PathVariable UUID reviewId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateCommentRequest request) {
        CommentResponse response = reviewService.createComment(reviewId, request, userDetails.getAccountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get comments", description = "Get all comments for a review")
    @GetMapping("/{reviewId}/comments")
    public ResponseEntity<List<CommentResponse>> getComments(
            @PathVariable String slug,
            @PathVariable UUID reviewId) {
        List<CommentResponse> response = reviewService.getComments(reviewId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Resolve comment", description = "Mark a comment as resolved")
    @PatchMapping("/{reviewId}/comments/{commentId}")
    public ResponseEntity<CommentResponse> resolveComment(
            @PathVariable String slug,
            @PathVariable UUID reviewId,
            @PathVariable UUID commentId) {
        CommentResponse response = reviewService.resolveComment(reviewId, commentId);
        return ResponseEntity.ok(response);
    }
}