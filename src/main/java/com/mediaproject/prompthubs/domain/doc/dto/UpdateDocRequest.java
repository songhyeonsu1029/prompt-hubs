package com.mediaproject.prompthubs.domain.doc.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateDocRequest {

    private String title;
    private String content;
    private String category;
}