package com.mediaproject.prompthubs.integration.notion.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotionPageOption {
    private String id;
    private String title;
    private String icon;
    private String url;
    private boolean archived;
}
