package com.example.ddangddangddang.domain.user.repository;

import com.example.ddangddangddang.domain.user.entity.SocialAccount;
import com.example.ddangddangddang.domain.user.entity.SocialProvider;
import com.example.ddangddangddang.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class SocialAccountRepositoryTest {

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private UserRepository userRepository;

    private Long userId;
    private Long otherUserId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(User.createSocial("a@test.com", "회원A", "01011111111")).getId();
        otherUserId = userRepository.save(User.createSocial("b@test.com", "회원B", "01022222222")).getId();
    }

    @Test
    @DisplayName("제공자와 제공자 회원번호로 연결을 찾는다")
    void findByProviderAndProviderUserId() {
        socialAccountRepository.save(SocialAccount.link(userId, SocialProvider.KAKAO, "1001"));

        assertThat(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "1001"))
                .get().extracting(SocialAccount::getUserId).isEqualTo(userId);
        assertThat(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "9999")).isEmpty();
        assertThat(socialAccountRepository.existsByUserIdAndProvider(userId, SocialProvider.KAKAO)).isTrue();
        assertThat(socialAccountRepository.existsByUserIdAndProvider(otherUserId, SocialProvider.KAKAO)).isFalse();
    }

    @Test
    @DisplayName("같은 카카오 계정을 두 회원에게 연결할 수 없다")
    void sameSocialAccountToTwoUsers() {
        socialAccountRepository.saveAndFlush(SocialAccount.link(userId, SocialProvider.KAKAO, "1001"));

        assertThatThrownBy(() -> socialAccountRepository.saveAndFlush(
                SocialAccount.link(otherUserId, SocialProvider.KAKAO, "1001")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("한 회원에게 카카오 계정을 두 개 연결할 수 없다")
    void twoSocialAccountsToOneUser() {
        socialAccountRepository.saveAndFlush(SocialAccount.link(userId, SocialProvider.KAKAO, "1001"));

        assertThatThrownBy(() -> socialAccountRepository.saveAndFlush(
                SocialAccount.link(userId, SocialProvider.KAKAO, "2002")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("소셜 가입 회원은 비밀번호 없이 저장된다")
    void socialUserWithoutPassword() {
        User user = userRepository.findById(userId).orElseThrow();

        assertThat(user.getPassword()).isNull();
        assertThat(user.hasPassword()).isFalse();
    }
}
