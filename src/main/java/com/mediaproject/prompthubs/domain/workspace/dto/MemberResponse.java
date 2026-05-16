package com.mediaproject.prompthubs.domain.workspace.dto;

import com.mediaproject.prompthubs.domain.workspace.entity.Member;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class MemberResponse {

    private UUID id;
    private UUID accountId;
    private String email;
    private String name;
    private String avatarUrl;
    private String role;
    private LocalDateTime joinedAt;

    public static MemberResponse from(Member member) {
        return MemberResponse.builder()
                .id(member.getId())
                .accountId(member.getAccount().getId())
                .email(member.getAccount().getEmail())
                .name(member.getAccount().getName())
                .avatarUrl(member.getAccount().getAvatarUrl())
                .role(member.getRole().name())
                .joinedAt(member.getJoinedAt())
                .build();
    }
}