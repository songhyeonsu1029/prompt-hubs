package com.mediaproject.prompthubs.domain.review.repository;

import com.mediaproject.prompthubs.domain.review.entity.PromptReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<PromptReview, UUID> {

    @Query("SELECT r FROM PromptReview r WHERE r.workspace.id = :workspaceId")
    Page<PromptReview> findByWorkspaceId(@Param("workspaceId") UUID workspaceId, Pageable pageable);

    @Query("SELECT r FROM PromptReview r WHERE r.workspace.id = :workspaceId AND r.status = :status")
    Page<PromptReview> findByWorkspaceIdAndStatus(
            @Param("workspaceId") UUID workspaceId,
            @Param("status") PromptReview.Status status,
            Pageable pageable);

    @Query("SELECT r FROM PromptReview r WHERE r.workspace.id = :workspaceId AND r.reviewer.id = :reviewerId")
    Page<PromptReview> findByWorkspaceIdAndReviewerId(
            @Param("workspaceId") UUID workspaceId,
            @Param("reviewerId") UUID reviewerId,
            Pageable pageable);

    @Query("SELECT r FROM PromptReview r WHERE r.workspace.id = :workspaceId AND r.requester.id = :requesterId")
    Page<PromptReview> findByWorkspaceIdAndRequesterId(
            @Param("workspaceId") UUID workspaceId,
            @Param("requesterId") UUID requesterId,
            Pageable pageable);

    @Query("SELECT r FROM PromptReview r WHERE r.workspace.id = :workspaceId AND r.version.prompt.id = :promptId")
    Page<PromptReview> findByWorkspaceIdAndPromptId(
            @Param("workspaceId") UUID workspaceId,
            @Param("promptId") UUID promptId,
            Pageable pageable);

    @Query("SELECT r FROM PromptReview r WHERE r.id = :id AND r.workspace.id = :workspaceId")
    Optional<PromptReview> findByIdAndWorkspaceId(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId);

    @Query("SELECT COUNT(r) FROM PromptReview r WHERE r.workspace.id = :workspaceId " +
           "AND r.reviewer.id = :reviewerId AND r.status = 'PENDING'")
    long countPendingByWorkspaceIdAndReviewerId(
            @Param("workspaceId") UUID workspaceId,
            @Param("reviewerId") UUID reviewerId);

    @Query("SELECT r FROM PromptReview r WHERE r.workspace.id = :workspaceId ORDER BY r.createdAt DESC")
    List<PromptReview> findRecentByWorkspaceId(@Param("workspaceId") UUID workspaceId, Pageable pageable);
}