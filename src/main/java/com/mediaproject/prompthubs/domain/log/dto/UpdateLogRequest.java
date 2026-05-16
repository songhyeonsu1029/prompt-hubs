package com.mediaproject.prompthubs.domain.log.dto;

import com.mediaproject.prompthubs.domain.log.entity.PromptLog;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class UpdateLogRequest {

    private String promptText;
    private String modelUsed;
    private String resultSummary;
    private PromptLog.Status status;
    private List<String> tags;
}