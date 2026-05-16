package com.mediaproject.prompthubs.domain.prompt.repository;

import com.mediaproject.prompthubs.domain.prompt.entity.PromptVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromptVersionRepository extends JpaRepository<PromptVersion, UUID> {

    @Query("SELECT v FROM PromptVersion v WHERE v.prompt.id = :promptId ORDER BY v.createdAt DESC")
    List<PromptVersion> findByPromptIdOrderByCreatedAtDesc(@Param("promptId") UUID promptId);

    @Query("SELECT v FROM PromptVersion v WHERE v.prompt.id = :promptId ORDER BY v.createdAt DESC LIMIT 1")
    Optional<PromptVersion> findLatestByPromptId(@Param("promptId") UUID promptId);

    @Query("SELECT COUNT(v) FROM PromptVersion v WHERE v.prompt.id = :promptId")
    long countByPromptId(@Param("promptId") UUID promptId);
}