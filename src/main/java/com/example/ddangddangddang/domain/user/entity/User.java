package com.example.ddangddangddang.domain.user.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    // 소셜 로그인으로만 가입한 유저는 비밀번호가 없다.
    private String password;

    @Column(nullable = false, unique = true, length = 20)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(unique = true, length = 11)
    private String phoneNumber;

    // 마지막으로 닉네임을 바꾼 시각. 가입 후 한 번도 바꾸지 않았으면 null (첫 변경은 바로 할 수 있다)
    private LocalDateTime nicknameChangedAt;

    private User(String email, String password, String nickname, String phoneNumber, UserRole role) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.phoneNumber = phoneNumber;
        this.role = role;
    }

    public static User create(String email, String encodedPassword, String nickname, String phoneNumber) {
        return new User(email, encodedPassword, nickname, phoneNumber, UserRole.USER);
    }

    // 소셜 로그인 가입. 비밀번호 없음
    public static User createSocial(String email, String nickname, String phoneNumber) {
        return new User(email,null, nickname, phoneNumber, UserRole.USER);
    }

    public boolean hasPassword() {
        return password != null;
    }

    /**
     * 휴대폰 인증이 필요 없는 테스트용
     */
    public static User create(String email, String encodedPassword, String nickname) {
        return create(email, encodedPassword, nickname, null);
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void changeNickname(String nickname, LocalDateTime now) {
        this.nickname = nickname;
        this.nicknameChangedAt = now;
    }

    /**
     * 다음에 닉네임을 바꿀 수 있는 시각. 지금 바로 바꿀 수 있으면 null
     *
     * @param interval 닉네임을 바꾼 뒤 다시 바꿀 수 있을 때까지의 기간
     */
    public LocalDateTime nicknameChangeAvailableAt(Duration interval, LocalDateTime now) {
        if (nicknameChangedAt == null) {
            return null;
        }
        LocalDateTime availableAt = nicknameChangedAt.plus(interval);
        return now.isBefore(availableAt) ? availableAt : null;
    }

    /** 개발용 관리자 계정 생성 (DevAdminInitializer). 운영에서는 쓰지 않는다 */
    public static User createAdmin(String email, String encodedPassword, String nickname) {
        return new User(email, encodedPassword, nickname, null, UserRole.ADMIN);
    }

    /** 관리자 권한 부여. 이미 로그인한 상태면 다시 로그인해야 반영된다 (권한이 토큰에 들어 있음) */
    public void grantAdmin() {
        this.role = UserRole.ADMIN;
    }
}