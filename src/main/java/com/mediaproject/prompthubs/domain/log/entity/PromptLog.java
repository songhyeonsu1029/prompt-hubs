package com.mediaproject.prompthubs.domain.log.entity;

import com.mediaproject.prompthubs.domain.account.entity.Account;
import com.mediaproject.prompthubs.domain.workspace.entity.Workspace;
import com.mediaproject.prompthubs.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "prompt_logs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_prompt_log_idempotency",
                        columnNames = {"workspace_id", "idempotency_key"}
                )
        },
        indexes = {
                @Index(name = "idx_prompt_log_session", columnList = "session_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PromptLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private Account author;

    @Column(name = "prompt_text", columnDefinition = "TEXT", nullable = false)
    private String promptText;

    @Column(name = "model_used", nullable = false)
    private String modelUsed;

    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    /**
     * Full assistant turn content — text, code blocks, terminal commands and their output.
     * Rendered as Markdown in the PromptHubs UI.
     */
    @Column(name = "result_body", columnDefinition = "TEXT")
    private String resultBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    /**
     * Identifier of the Claude Code (or other) session this log belongs to. Lets the UI
     * group all turns of one conversation together. Optional.
     */
    @Column(name = "session_id", columnDefinition = "uuid")
    private UUID sessionId;

    /**
     * Caller-supplied idempotency key. Together with workspace_id forms a unique constraint,
     * so the same hook firing twice (e.g. on Claude Code retry) inserts only one row.
     */
    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    /**
     * Optional structured details — turn-level metadata, tool-call list, model parameters,
     * anything caller wants to record alongside the rendered text.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    public enum Status {
        /** 의도대로 잘 끝남. */
        SUCCESS,
        /** 일부 도구 호출이 실패했지만 결과는 살아 있음 (auto-save 가 tool_result.is_error 보고 자동 분류). */
        PARTIAL,
        /** 어시스턴트가 에러로 끝남 (API 에러 / fatal). */
        FAILURE,
        /** 사람이 한 번 더 봐야 함. 자동 분류는 안 하고 UI 에서 수동 지정. */
        NEEDS_REVIEW
    }

    public void update(String promptText, String modelUsed, String resultSummary, Status status, List<String> tags) {
        if (promptText != null) this.promptText = promptText;
        if (modelUsed != null) this.modelUsed = modelUsed;
        if (resultSummary != null) this.resultSummary = resultSummary;
        if (status != null) this.status = status;
        if (tags != null) this.tags = tags;
    }

    public void updateBody(String resultBody, Map<String, Object> metadata) {
        if (resultBody != null) this.resultBody = resultBody;
        if (metadata != null) this.metadata = metadata;
    }
}