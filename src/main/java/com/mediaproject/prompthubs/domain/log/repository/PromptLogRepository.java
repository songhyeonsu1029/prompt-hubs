package com.mediaproject.prompthubs.domain.log.repository;

import com.mediaproject.prompthubs.domain.log.entity.PromptLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromptLogRepository extends JpaRepository<PromptLog, UUID> {

    @Query("SELECT l FROM PromptLog l WHERE l.workspace.id = :workspaceId")
    Page<PromptLog> findByWorkspaceId(@Param("workspaceId") UUID workspaceId, Pageable pageable);

    @Query("SELECT l FROM PromptLog l WHERE l.workspace.id = :workspaceId AND l.status = :status")
    Page<PromptLog> findByWorkspaceIdAndStatus(
            @Param("workspaceId") UUID workspaceId,
            @Param("status") PromptLog.Status status,
            Pageable pageable);

    @Query("SELECT l FROM PromptLog l WHERE l.workspace.id = :workspaceId AND l.author.id = :authorId")
    Page<PromptLog> findByWorkspaceIdAndAuthorId(
            @Param("workspaceId") UUID workspaceId,
            @Param("authorId") UUID authorId,
            Pageable pageable);

    @Query("SELECT l FROM PromptLog l WHERE l.workspace.id = :workspaceId " +
           "AND (LOWER(l.promptText) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(l.resultSummary) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<PromptLog> findByWorkspaceIdAndKeyword(
            @Param("workspaceId") UUID workspaceId,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("SELECT l FROM PromptLog l WHERE l.id = :id AND l.workspace.id = :workspaceId")
    Optional<PromptLog> findByIdAndWorkspaceId(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId);

    // Retention-aware queries (Free plan: 30-day cutoff)
    @Query("SELECT l FROM PromptLog l WHERE l.workspace.id = :workspaceId AND l.createdAt >= :cutoff")
    Page<PromptLog> findByWorkspaceIdAndCreatedAtAfter(
            @Param("workspaceId") UUID workspaceId,
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable);

    @Query("SELECT l FROM PromptLog l WHERE l.workspace.id = :workspaceId AND l.status = :status AND l.createdAt >= :cutoff")
    Page<PromptLog> findByWorkspaceIdAndStatusAndCreatedAtAfter(
            @Param("workspaceId") UUID workspaceId,
            @Param("status") PromptLog.Status status,
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable);

    @Query("SELECT l FROM PromptLog l WHERE l.workspace.id = :workspaceId AND l.author.id = :authorId AND l.createdAt >= :cutoff")
    Page<PromptLog> findByWorkspaceIdAndAuthorIdAndCreatedAtAfter(
            @Param("workspaceId") UUID workspaceId,
            @Param("authorId") UUID authorId,
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable);

    @Query("SELECT l FROM PromptLog l WHERE l.workspace.id = :workspaceId " +
           "AND (LOWER(l.promptText) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(l.resultSummary) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND l.createdAt >= :cutoff")
    Page<PromptLog> findByWorkspaceIdAndKeywordAndCreatedAtAfter(
            @Param("workspaceId") UUID workspaceId,
            @Param("keyword") String keyword,
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable);

    @Query("SELECT COUNT(l) FROM PromptLog l WHERE l.workspace.id = :workspaceId")
    long countByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    @Query("SELECT l FROM PromptLog l WHERE l.workspace.id = :workspaceId ORDER BY l.createdAt DESC")
    List<PromptLog> findRecentByWorkspaceId(@Param("workspaceId") UUID workspaceId, Pageable pageable);

    Optional<PromptLog> findByWorkspaceIdAndIdempotencyKey(UUID workspaceId, String idempotencyKey);
}