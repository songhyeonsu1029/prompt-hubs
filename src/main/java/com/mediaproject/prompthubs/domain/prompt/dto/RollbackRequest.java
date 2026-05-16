package com.mediaproject.prompthubs.domain.prompt.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class RollbackRequest {

    @NotNull(message = "Target version ID is required")
    private UUID targetVersionId;
}