package com.mediaproject.prompthubs.integration.notion.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SelectParentPageRequest {

    @NotBlank
    private String pageId;
}
