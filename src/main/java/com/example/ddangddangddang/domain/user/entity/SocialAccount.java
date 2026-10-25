package com.example.ddangddangddang.domain.user.entity;

// 회원과 소셜 계정 연결

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "social_accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_social_account_provider_user",
                        columnNames = {"provider", "provider_user_id"}),
                @UniqueConstraint(name = "uk_social_account_user_provider",
                        columnNames = {"user_id", "provider"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SocialProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 64)
    private String providerUserId;

    private SocialAccount(Long userId, SocialProvider provider, String providerUserId) {
        this.userId = userId;
        this.provider = provider;
        this.providerUserId = providerUserId;
    }

    public static SocialAccount link(Long userId, SocialProvider provider, String providerUserId) {
        return new SocialAccount(userId, provider, providerUserId);
    }
}