package com.mediaproject.prompthubs.domain.prompt.repository;

import com.mediaproject.prompthubs.domain.prompt.entity.Prompt;
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
public interface PromptRepository extends JpaRepository<Prompt, UUID> {

    @Query("SELECT p FROM Prompt p WHERE p.workspace.id = :workspaceId")
    Page<Prompt> findByWorkspaceId(@Param("workspaceId") UUID workspaceId, Pageable pageable);

    @Query("SELECT p FROM Prompt p WHERE p.workspace.id = :workspaceId AND p.status = :status")
    Page<Prompt> findByWorkspaceIdAndStatus(
            @Param("workspaceId") UUID workspaceId,
            @Param("status") Prompt.Status status,
            Pageable pageable);

    @Query("SELECT p FROM Prompt p WHERE p.workspace.id = :workspaceId AND p.category = :category")
    Page<Prompt> findByWorkspaceIdAndCategory(
            @Param("workspaceId") UUID workspaceId,
            @Param("category") String category,
            Pageable pageable);

    @Query("SELECT p FROM Prompt p WHERE p.workspace.id = :workspaceId " +
           "AND LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Prompt> findByWorkspaceIdAndKeyword(
            @Param("workspaceId") UUID workspaceId,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("SELECT p FROM Prompt p WHERE p.id = :id AND p.workspace.id = :workspaceId")
    Optional<Prompt> findByIdAndWorkspaceId(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId);

    @Query("SELECT COUNT(p) FROM Prompt p WHERE p.workspace.id = :workspaceId")
    long countByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    @Query("SELECT p FROM Prompt p WHERE p.workspace.id = :workspaceId ORDER BY p.createdAt DESC")
    List<Prompt> findRecentByWorkspaceId(@Param("workspaceId") UUID workspaceId, Pageable pageable);
}