package com.mediaproject.prompthubs.domain.workspace.repository;

import com.mediaproject.prompthubs.domain.workspace.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemberRepository extends JpaRepository<Member, UUID> {

    @Query("SELECT m FROM Member m JOIN FETCH m.workspace w " +
           "WHERE m.account.id = :accountId AND w.deletedAt IS NULL")
    List<Member> findByAccountIdWithWorkspace(@Param("accountId") UUID accountId);

    @Query("SELECT m FROM Member m " +
           "WHERE m.account.id = :accountId AND m.workspace.id = :workspaceId")
    Optional<Member> findByAccountIdAndWorkspaceId(
            @Param("accountId") UUID accountId,
            @Param("workspaceId") UUID workspaceId
    );

    @Query("SELECT m FROM Member m JOIN FETCH m.account " +
           "WHERE m.workspace.id = :workspaceId")
    List<Member> findByWorkspaceIdWithAccount(@Param("workspaceId") UUID workspaceId);

    boolean existsByAccountIdAndWorkspaceId(UUID accountId, UUID workspaceId);

    @Query("SELECT COUNT(m) FROM Member m WHERE m.workspace.id = :workspaceId")
    long countByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    @Query("SELECT m FROM Member m WHERE m.workspace.id = :workspaceId AND m.role = 'OWNER'")
    Optional<Member> findOwnerByWorkspaceId(@Param("workspaceId") UUID workspaceId);
}
