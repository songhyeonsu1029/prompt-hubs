package com.mediaproject.prompthubs.domain.workspace.dto;

import com.mediaproject.prompthubs.domain.workspace.entity.Member;
import com.mediaproject.prompthubs.domain.workspace.entity.Workspace;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class WorkspaceResponse {

    private UUID id;
    private String name;
    private String slug;
    private String plan;
    private String myRole;
    private LocalDateTime createdAt;

    public static WorkspaceResponse from(Workspace workspace) {
        return WorkspaceResponse.builder()
                .id(workspace.getId())
                .name(workspace.getName())
                .slug(workspace.getSlug())
                .plan(workspace.getPlan().name())
                .createdAt(workspace.getCreatedAt())
                .build();
    }

    public static WorkspaceResponse from(Workspace workspace, Member.Role role) {
        return WorkspaceResponse.builder()
                .id(workspace.getId())
                .name(workspace.getName())
                .slug(workspace.getSlug())
                .plan(workspace.getPlan().name())
                .myRole(role.name())
                .createdAt(workspace.getCreatedAt())
                .build();
    }
}