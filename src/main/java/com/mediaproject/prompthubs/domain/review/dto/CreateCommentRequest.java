package com.mediaproject.prompthubs.domain.review.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateCommentRequest {

    private Integer lineStart;
    private Integer lineEnd;

    @NotBlank(message = "Comment content is required")
    private String content;
}