package com.example.ddangddangddang.domain.user.dev;

import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.entity.UserRole;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DevAdminInitializerTest {

    @InjectMocks
    private DevAdminInitializer initializer;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private void configure(String email, String password) {
        ReflectionTestUtils.setField(initializer, "email", email);
        ReflectionTestUtils.setField(initializer, "password", password);
        ReflectionTestUtils.setField(initializer, "nickname", "관리자");
    }

    @Test
    @DisplayName("이메일 설정이 비어 있으면 아무것도 하지 않는다")
    void skipWhenBlank() {
        configure("", "admin1234!");

        initializer.run(null);

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("회원이 없으면 관리자 계정을 새로 만든다")
    void create() {
        configure("admin@ddang.local", "admin1234!");
        given(userRepository.findByEmail("admin@ddang.local")).willReturn(Optional.empty());
        given(passwordEncoder.encode("admin1234!")).willReturn("encoded");

        initializer.run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(saved.getValue().getPassword()).isEqualTo("encoded");
    }

    @Test
    @DisplayName("이미 있는 회원이면 비밀번호는 그대로 두고 관리자 권한만 준다")
    void grantExisting() {
        configure("me@ddang.com", "admin1234!");
        User user = User.create("me@ddang.com", "original", "나");
        given(userRepository.findByEmail("me@ddang.com")).willReturn(Optional.of(user));

        initializer.run(null);

        assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(user.getPassword()).isEqualTo("original");
        verify(userRepository, never()).save(any());
    }
}
