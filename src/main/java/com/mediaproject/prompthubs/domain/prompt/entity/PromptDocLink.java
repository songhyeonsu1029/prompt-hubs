package com.mediaproject.prompthubs.domain.prompt.entity;

import com.mediaproject.prompthubs.domain.doc.entity.Doc;
import com.mediaproject.prompthubs.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "prompt_doc_links",
        uniqueConstraints = @UniqueConstraint(columnNames = {"prompt_id", "doc_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PromptDocLink extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prompt_id", nullable = false)
    private Prompt prompt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doc_id", nullable = false)
    private Doc doc;
}
