package com.mediaproject.prompthubs.domain.prompt.entity;

import com.mediaproject.prompthubs.domain.account.entity.Account;
import com.mediaproject.prompthubs.domain.workspace.entity.Workspace;
import com.mediaproject.prompthubs.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "prompts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Prompt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private Account author;

    @Column(nullable = false)
    private String title;

    private String category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.DRAFT;

    @Column(name = "source_log_id")
    private UUID sourceLogId;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    public enum Status {
        DRAFT, IN_REVIEW, APPROVED
    }

    public void setCurrentVersion(UUID versionId) {
        this.currentVersionId = versionId;
    }

    public void updateStatus(Status status) {
        this.status = status;
    }

    public void update(String title, String category, List<String> tags) {
        if (title != null) this.title = title;
        if (category != null) this.category = category;
        if (tags != null) this.tags = tags;
    }
}
