package com.mediaproject.prompthubs.domain.prompt.repository;

import com.mediaproject.prompthubs.domain.prompt.entity.PromptDocLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PromptDocLinkRepository extends JpaRepository<PromptDocLink, UUID> {

    @Query("SELECT l FROM PromptDocLink l JOIN FETCH l.doc WHERE l.prompt.id = :promptId")
    List<PromptDocLink> findByPromptIdWithDoc(@Param("promptId") UUID promptId);

    @Query("SELECT l FROM PromptDocLink l JOIN FETCH l.prompt WHERE l.doc.id = :docId")
    List<PromptDocLink> findByDocIdWithPrompt(@Param("docId") UUID docId);

    void deleteByPromptId(UUID promptId);

    boolean existsByPromptIdAndDocId(UUID promptId, UUID docId);
}