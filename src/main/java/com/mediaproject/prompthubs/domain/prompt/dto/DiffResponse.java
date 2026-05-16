package com.mediaproject.prompthubs.domain.prompt.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class DiffResponse {

    private UUID sourceVersionId;
    private String sourceVersionNumber;
    private UUID targetVersionId;
    private String targetVersionNumber;
    private List<DiffLine> promptTextDiff;
    private List<DiffLine> successCriteriaDiff;
    private List<DiffLine> validationMethodDiff;

    @Getter
    @Builder
    public static class DiffLine {
        private int lineNumber;
        private String content;
        private ChangeType changeType;

        public enum ChangeType {
            UNCHANGED, ADDED, DELETED
        }
    }
}
