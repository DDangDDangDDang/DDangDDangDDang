package com.example.ddangddangddang.domain.auth.verification;

import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

/**
 * 인증번호 발급과 검증.
 * <p>
 * 1. issueCode: 6자리 인증번호를 만들어 저장한다. (발송은 호출하는 쪽에서)
 * 2. verifyCode: 인증번호가 맞으면 "인증 완료 토큰"을 돌려준다.
 * 3. consumeToken: 다음 단계(가입, 비밀번호 재설정)에서 토큰을 한 번만 쓸 수 있게 꺼낸다.
 * <p>
 * 인증번호를 다음 단계까지 계속 들고 다니지 않고 토큰으로 바꾸는 이유:
 * 인증번호는 6자리라 추측하기 쉬워 짧게 쓰고 버려야 하고, 토큰은 추측할 수 없는 UUID라 안전하다.
 */
@Service
@RequiredArgsConstructor
public class VerificationService {

    public static final Duration CODE_TTL = Duration.ofMinutes(5);
    public static final Duration TOKEN_TTL = Duration.ofMinutes(10);
    public static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);
    public static final int MAX_ATTEMPTS = 5;
    public static final int DAILY_LIMIT_PER_TARGET = 10;
    public static final Duration DAILY_WINDOW = Duration.ofDays(1);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final VerificationCodeRepository verificationCodeRepository;
    private final VerificationRateLimitRepository rateLimitRepository;

    public String issueCode(VerificationPurpose purpose, String target) {
        if (!verificationCodeRepository.tryStartCooldown(purpose, target, RESEND_COOLDOWN)) {
            throw new BusinessException(VerificationErrorCode.RESEND_TOO_SOON);
        }
        if (rateLimitRepository.increaseTargetCount(target, DAILY_WINDOW) > DAILY_LIMIT_PER_TARGET) {
            throw new BusinessException(VerificationErrorCode.DAILY_LIMIT_EXCEEDED);
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        verificationCodeRepository.saveCode(purpose, target, code, CODE_TTL);
        return code;
    }

    /**
     * 6자리는 경우의 수가 100만 개뿐이라 무작위 대입을 막기 위해 시도 횟수를 제한한다.
     * 5번 틀리면 인증번호를 지워 다시 요청하게 한다.
     * 확인·횟수 증가·삭제는 저장소에서 한 번에(원자적으로) 처리해, 동시에 맞는 번호로 요청해도 토큰은 하나만 발급된다.
     */
    public String verifyCode(VerificationPurpose purpose, String target, String code) {
        VerificationCodeRepository.CodeCheckResult result = verificationCodeRepository.verifyAndDeleteCode(
                purpose, target, code, CODE_TTL, MAX_ATTEMPTS);
        if (result == VerificationCodeRepository.CodeCheckResult.TOO_MANY_ATTEMPTS) {
            throw new BusinessException(VerificationErrorCode.TOO_MANY_ATTEMPTS);
        }
        if (result == VerificationCodeRepository.CodeCheckResult.INVALID) {
            throw new BusinessException(VerificationErrorCode.INVALID_CODE);
        }

        String token = UUID.randomUUID().toString();
        verificationCodeRepository.saveToken(purpose, token, target, TOKEN_TTL);
        return token;
    }

    /**
     * 인증 완료 토큰을 꺼내 인증된 대상(휴대폰 번호, 이메일)을 돌려준다. 한 번 쓰면 사라진다.
     */
    public String consumeToken(VerificationPurpose purpose, String token) {
        return verificationCodeRepository.consumeToken(purpose, token)
                .orElseThrow(() -> new BusinessException(VerificationErrorCode.INVALID_VERIFICATION_TOKEN));
    }

    /**
     * 토큰이 expectedTarget(예: 가입 요청의 휴대폰 번호)을 인증한 것일 때만 쓴다.
     * 다른 번호의 토큰이면 예외를 던지고 토큰은 남겨 둔다. (다른 번호로 인증하고 남의 번호를 보내는 것 방지)
     */
    public void consumeMatchingToken(VerificationPurpose purpose, String token, String expectedTarget) {
        if (!verificationCodeRepository.consumeTokenIfMatches(purpose, token, expectedTarget)) {
            throw new BusinessException(VerificationErrorCode.INVALID_VERIFICATION_TOKEN);
        }
    }
}