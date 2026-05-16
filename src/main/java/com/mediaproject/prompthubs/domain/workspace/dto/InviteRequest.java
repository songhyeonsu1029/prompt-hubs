package com.mediaproject.prompthubs.domain.workspace.dto;

import com.mediaproject.prompthubs.domain.workspace.entity.Member;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class InviteRequest {

    @NotNull(message = "Role is required")
    private Member.Role role;
}