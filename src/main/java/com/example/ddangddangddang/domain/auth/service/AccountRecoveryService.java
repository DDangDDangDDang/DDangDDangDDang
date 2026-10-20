package com.example.ddangddangddang.domain.auth.service;

import com.example.ddangddangddang.domain.auth.dto.FindEmailResponse;
import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationErrorCode;
import com.example.ddangddangddang.domain.auth.verification.VerificationPurpose;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.message.EmailSender;
import com.example.ddangddangddang.global.message.SmsSender;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static com.example.ddangddangddang.global.message.SmtpEmailSender.maskEmail;

/**
 * 이메일 찾기와 비밀번호 재설정
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountRecoveryService {

    private final VerificationService verificationService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsSender smsSender;
    private final EmailSender emailSender;

    /**
     * 가입 여부와 상관없이 인증번호를 보냄
     * 휴대폰 주인임을 확인 후 가입 여부를 알려줌
     */
    public long sendFindEmailCode(String phoneNumber) {
        String code = verificationService.issueCode(VerificationPurpose.FIND_EMAIL, phoneNumber);
        smsSender.send(phoneNumber, "[땅땅땅] 이메일 찾기 인증번호 [" + code + "]를 입력해 주새요.");

        return VerificationService.CODE_TTL.toSeconds();
    }

    public FindEmailResponse findEmail(String phoneNumber, String code) {
        String token = verificationService.verifyCode(VerificationPurpose.FIND_EMAIL, phoneNumber, code);
        verificationService.consumeToken(VerificationPurpose.FIND_EMAIL, token);

        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        return new FindEmailResponse(maskEmail(user.getEmail()), user.getCreatedAt().toLocalDate());
    }

    public long sendPasswordResetCode(String email) {
        String code = verificationService.issueCode(VerificationPurpose.RESET_PASSWORD, email);
        Optional<User> user = userRepository.findByEmail(email);
        if (user.isPresent()) {
            emailSender.send(email, "[땅땅땅] 비밀번호 재설정 인증번호",
                    "비밀번호 재설정 인증번호는 [" + code + "]입니다. 5분 안에 입력해 주세요.\n"
                            + "본인이 요청하지 않았다면 이 메일을 무시해 주세요.");
        }
        return VerificationService.CODE_TTL.toSeconds();
    }

    public String verifyPasswordResetCode(String email, String code) {
        return verificationService.verifyCode(VerificationPurpose.RESET_PASSWORD, email, code);
    }

    /**
     * 비밀번호를 바꾸고 모든 기기에서 로그아웃
     */
    @Transactional
    public void resetPassword(String verificationToken, String newPassword) {
        String email = verificationService.consumeToken(VerificationPurpose.RESET_PASSWORD, verificationToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(VerificationErrorCode.INVALID_VERIFICATION_TOKEN));

        user.changePassword(passwordEncoder.encode(newPassword));
        refreshTokenRepository.deleteByUserId(user.getId());
    }

    /**
     * user@test.com → us**@test.com
     */
    static String maskEmail(String email) {
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        int visible = local.length() <= 2 ? 1 : 2;
        return local.substring(0, visible) + "*".repeat(local.length() - visible) + email.substring(at);
    }
}
