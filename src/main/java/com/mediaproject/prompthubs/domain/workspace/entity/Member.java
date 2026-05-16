package com.mediaproject.prompthubs.domain.workspace.entity;

import com.mediaproject.prompthubs.domain.account.entity.Account;
import com.mediaproject.prompthubs.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "members", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"account_id", "workspace_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Member extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "joined_at", nullable = false)
    @Builder.Default
    private LocalDateTime joinedAt = LocalDateTime.now();

    public enum Role {
        OWNER, ADMIN, MEMBER;

        public boolean isHigherOrEqualThan(Role other) {
            return this.ordinal() <= other.ordinal();
        }
    }

    public void changeRole(Role newRole) {
        this.role = newRole;
    }

    public boolean isOwner() {
        return this.role == Role.OWNER;
    }

    public boolean isAdmin() {
        return this.role == Role.ADMIN || this.role == Role.OWNER;
    }
}