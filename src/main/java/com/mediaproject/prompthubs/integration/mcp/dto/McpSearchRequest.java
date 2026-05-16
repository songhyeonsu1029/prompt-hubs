package com.mediaproject.prompthubs.integration.mcp.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class McpSearchRequest {
    private String keyword;
    private String category;
}
