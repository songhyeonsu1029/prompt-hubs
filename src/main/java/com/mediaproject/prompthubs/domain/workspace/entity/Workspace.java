package com.mediaproject.prompthubs.domain.workspace.entity;

import com.mediaproject.prompthubs.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "workspaces")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Workspace extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Plan plan = Plan.FREE;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public enum Plan {
        FREE, PRO, ENTERPRISE
    }

    public void update(String name, String slug) {
        if (name != null) {
            this.name = name;
        }
        if (slug != null) {
            this.slug = slug;
        }
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}