package com.example.ddangddangddang.domain.user.dev;

import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.entity.UserRole;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 개발용 관리자 계정을 서버 시작 시 만든다.
 *
 * - app.dev-admin.email(DEV_ADMIN_EMAIL)이 비어 있으면 아무것도 하지 않는다. (기본값: 비어 있음)
 * - 그 이메일의 회원이 없으면 관리자 계정을 새로 만들고, 있으면 관리자 권한만 준다. (비밀번호는 바꾸지 않음)
 * - 운영(prod 프로필)에서는 이 클래스 자체가 등록되지 않는다.
 */
@Slf4j
@Component
@Profile("!prod")
@RequiredArgsConstructor
public class DevAdminInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.dev-admin.email:}")
    private String email;

    @Value("${app.dev-admin.password:}")
    private String password;

    @Value("${app.dev-admin.nickname:관리자}")
    private String nickname;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank()) {
            return;
        }
        String adminEmail = email.strip();

        userRepository.findByEmail(adminEmail).ifPresentOrElse(
                user -> {
                    if (user.getRole() != UserRole.ADMIN) {
                        user.grantAdmin();
                        log.info("[개발용 관리자] 기존 회원에게 관리자 권한을 줬습니다. email={}", adminEmail);
                    }
                },
                () -> create(adminEmail)
        );
    }

    private void create(String adminEmail) {
        if (password == null || password.isBlank()) {
            log.warn("[개발용 관리자] DEV_ADMIN_PASSWORD가 비어 있어 계정을 만들지 않았습니다. email={}", adminEmail);
            return;
        }
        if (userRepository.existsByNickname(nickname)) {
            log.warn("[개발용 관리자] 닉네임 '{}'이 이미 있어 계정을 만들지 않았습니다. DEV_ADMIN_NICKNAME을 바꿔 주세요.", nickname);
            return;
        }
        userRepository.save(User.createAdmin(adminEmail, passwordEncoder.encode(password), nickname));
        log.info("[개발용 관리자] 관리자 계정을 만들었습니다. email={}", adminEmail);
    }
}
