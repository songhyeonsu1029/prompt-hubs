package com.mediaproject.prompthubs.domain.account.entity;

import com.mediaproject.prompthubs.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Account extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "avatar_url")
    private String avatarUrl;

    public enum Provider {
        LOCAL, GOOGLE, GITHUB
    }

    public void updateProfile(String name, String avatarUrl) {
        if (name != null) {
            this.name = name;
        }
        if (avatarUrl != null) {
            this.avatarUrl = avatarUrl;
        }
    }
}