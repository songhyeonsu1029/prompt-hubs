package com.mediaproject.prompthubs.domain.review.dto;

import com.mediaproject.prompthubs.domain.review.entity.ReviewComment;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class CommentResponse {

    private UUID id;
    private UUID reviewId;
    private UUID authorId;
    private String authorName;
    private Integer lineStart;
    private Integer lineEnd;
    private String content;
    private boolean resolved;
    private LocalDateTime createdAt;

    public static CommentResponse from(ReviewComment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .reviewId(comment.getReview().getId())
                .authorId(comment.getAuthor().getId())
                .authorName(comment.getAuthor().getName())
                .lineStart(comment.getLineStart())
                .lineEnd(comment.getLineEnd())
                .content(comment.getContent())
                .resolved(comment.isResolved())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}