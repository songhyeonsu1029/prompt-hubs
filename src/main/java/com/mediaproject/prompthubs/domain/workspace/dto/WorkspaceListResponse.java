package com.mediaproject.prompthubs.domain.workspace.dto;

import com.mediaproject.prompthubs.domain.workspace.entity.Member;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class WorkspaceListResponse {

    private UUID id;
    private String name;
    private String slug;
    private String plan;
    private String myRole;
    private LocalDateTime createdAt;

    public static WorkspaceListResponse from(Member member) {
        return WorkspaceListResponse.builder()
                .id(member.getWorkspace().getId())
                .name(member.getWorkspace().getName())
                .slug(member.getWorkspace().getSlug())
                .plan(member.getWorkspace().getPlan().name())
                .myRole(member.getRole().name())
                .createdAt(member.getWorkspace().getCreatedAt())
                .build();
    }
}