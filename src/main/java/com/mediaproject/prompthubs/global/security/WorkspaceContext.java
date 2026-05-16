package com.mediaproject.prompthubs.global.security;

import com.mediaproject.prompthubs.domain.workspace.entity.Member;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class WorkspaceContext {

    private final UUID workspaceId;
    private final String workspaceSlug;
    private final Member.Role role;

    public boolean isOwner() {
        return role == Member.Role.OWNER;
    }

    public boolean isAdmin() {
        return role == Member.Role.OWNER || role == Member.Role.ADMIN;
    }
}