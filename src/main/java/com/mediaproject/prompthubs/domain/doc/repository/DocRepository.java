package com.mediaproject.prompthubs.domain.doc.repository;

import com.mediaproject.prompthubs.domain.doc.entity.Doc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocRepository extends JpaRepository<Doc, UUID> {

    @Query("SELECT d FROM Doc d WHERE d.workspace.id = :workspaceId")
    Page<Doc> findByWorkspaceId(@Param("workspaceId") UUID workspaceId, Pageable pageable);

    @Query("SELECT d FROM Doc d WHERE d.workspace.id = :workspaceId AND d.category = :category")
    Page<Doc> findByWorkspaceIdAndCategory(
            @Param("workspaceId") UUID workspaceId,
            @Param("category") String category,
            Pageable pageable);

    @Query("SELECT d FROM Doc d WHERE d.id = :id AND d.workspace.id = :workspaceId")
    Optional<Doc> findByIdAndWorkspaceId(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId);
}