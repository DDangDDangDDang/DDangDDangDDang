package com.example.ddangddangddang.domain.user.service;

import com.example.ddangddangddang.domain.auth.exception.AuthErrorCode;
import com.example.ddangddangddang.domain.auth.repository.LoginAttemptRepository;
import com.example.ddangddangddang.domain.auth.service.AuthService;
import com.example.ddangddangddang.domain.auth.verification.VerificationPurpose;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.domain.user.dto.MyInfoResponse;
import com.example.ddangddangddang.domain.user.dto.MyInfoUpdateResponse;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.message.EmailSender;
import com.example.ddangddangddang.global.message.SmsSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static com.example.ddangddangddang.global.message.SmtpEmailSender.maskEmail;

/**
 * 이메일·휴대폰 번호 변경. 새 이메일·번호로 인증번호를 보내고, 맞게 입력하면 바꾼다.
 * 이메일은 로그인 아이디이고 휴대폰 번호는 이메일 찾기에 쓰므로, 본인 것인지 확인한 값만 저장한다.
 *
 * 바꾸기 전에 본인임을 다시 확인한다(재확인 토큰). 비밀번호가 있으면 비밀번호로, 소셜 로그인으로만 가입했으면 지금 휴대폰 번호로 받은 인증번호로.
 * 로그인 토큰만 빼앗긴 사람이 이메일을 자기 것으로 바꾼 뒤 비밀번호를 재설정해 계정을 가져가지 못하게 하기 위함이다.
 * 바뀐 뒤에는 이전 이메일(휴대폰 번호 변경은 지금 이메일)로 안내 메일을 보낸다.
 *
 * 인증번호는 새 값(이메일·번호) 기준으로 발급해 가입·비밀번호 재설정과 같은 재전송 대기·하루 제한을 받는다.
 * 중복은 먼저 조회로 확인하고, 두 회원이 동시에 같은 값으로 바꾸는 경우는 유니크 제약이 막는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserContactService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final VerificationService verificationService;
    private final EmailSender emailSender;
    private final SmsSender smsSender;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptRepository loginAttemptRepository;

    // ===== 본인 재확인 =====

    /**
     * 비밀번호로 본인임을 확인하고 재확인 토큰을 준다. (10분, 한 번 쓰면 사라짐)
     * 틀린 횟수는 로그인과 같이 센다. 로그인 토큰을 가진 사람이 비밀번호를 무작위로 대입하지 못하게 하기 위함
     */
    public String reauthWithPassword(Long userId, String password) {
        User user = userService.findUser(userId);
        if (!user.hasPassword()) {
            throw new BusinessException(UserErrorCode.WRONG_PASSWORD);
        }
        if (loginAttemptRepository.increaseFailureCount(user.getEmail(), AuthService.LOGIN_FAILURE_WINDOW)
                > AuthService.MAX_LOGIN_FAILURES) {
            throw new BusinessException(AuthErrorCode.TOO_MANY_LOGIN_FAILURES);
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(UserErrorCode.WRONG_PASSWORD);
        }
        loginAttemptRepository.clearFailures(user.getEmail());
        return verificationService.issueToken(VerificationPurpose.REAUTH, reauthTarget(userId));
    }

    /** 지금 휴대폰 번호로 본인 확인 인증번호를 보낸다. (비밀번호가 없는 소셜 가입 회원용) */
    public long sendReauthCode(Long userId) {
        User user = userService.findUser(userId);
        if (user.getPhoneNumber() == null) {
            throw new BusinessException(UserErrorCode.NO_PHONE_NUMBER);
        }
        String code = verificationService.issueCode(VerificationPurpose.REAUTH, reauthTarget(userId));
        smsSender.send(user.getPhoneNumber(), "[땅땅땅] 본인 확인 인증번호 [" + code + "]를 입력해 주세요.");
        return VerificationService.CODE_TTL.toSeconds();
    }

    public String verifyReauthCode(Long userId, String code) {
        return verificationService.verifyCode(VerificationPurpose.REAUTH, reauthTarget(userId), code);
    }

    // ===== 변경 =====

    public long sendEmailChangeCode(Long userId, String email) {
        validateNewEmail(userService.findUser(userId), email);
        String code = verificationService.issueCode(VerificationPurpose.CHANGE_EMAIL, email);
        emailSender.send(email, "[땅땅땅] 이메일 변경 인증번호",
                "이메일 변경 인증번호는 [" + code + "]입니다. 5분 안에 입력해 주세요.\n"
                        + "본인이 요청하지 않았다면 이 메일을 무시해 주세요.");
        return VerificationService.CODE_TTL.toSeconds();
    }

    /** 이메일을 바꾸고 새 이메일이 담긴 Access Token을 돌려준다. 이전 이메일로 바뀌었다는 안내를 보낸다. */
    @Transactional
    public MyInfoUpdateResponse changeEmail(Long userId, String email, String code, String reauthToken) {
        User user = userService.findUser(userId);
        validateNewEmail(user, email);
        verifyWithReauth(userId, VerificationPurpose.CHANGE_EMAIL, email, code, reauthToken);

        String previousEmail = user.getEmail();
        user.changeEmail(email);
        flush(UserErrorCode.DUPLICATE_EMAIL);
        notifyAfterCommit(previousEmail, "[땅땅땅] 로그인 이메일이 바뀌었어요",
                "로그인 이메일이 " + maskEmail(email) + "(으)로 바뀌었어요.\n"
                        + "본인이 바꾸지 않았다면 고객센터로 바로 알려 주세요.");
        return userService.withNewAccessToken(user);
    }

    public long sendPhoneNumberChangeCode(Long userId, String phoneNumber) {
        validateNewPhoneNumber(userService.findUser(userId), phoneNumber);
        String code = verificationService.issueCode(VerificationPurpose.CHANGE_PHONE, phoneNumber);
        smsSender.send(phoneNumber, "[땅땅땅] 휴대폰 번호 변경 인증번호 [" + code + "]를 입력해 주세요.");
        return VerificationService.CODE_TTL.toSeconds();
    }

    /** 휴대폰 번호를 바꾸고, 지금 이메일로 바뀌었다는 안내를 보낸다. */
    @Transactional
    public MyInfoResponse changePhoneNumber(Long userId, String phoneNumber, String code, String reauthToken) {
        User user = userService.findUser(userId);
        validateNewPhoneNumber(user, phoneNumber);
        verifyWithReauth(userId, VerificationPurpose.CHANGE_PHONE, phoneNumber, code, reauthToken);

        user.changePhoneNumber(phoneNumber);
        flush(UserErrorCode.DUPLICATE_PHONE_NUMBER);
        notifyAfterCommit(user.getEmail(), "[땅땅땅] 휴대폰 번호가 바뀌었어요",
                "계정의 휴대폰 번호가 바뀌었어요. 이메일 찾기도 새 번호로 해요.\n"
                        + "본인이 바꾸지 않았다면 고객센터로 바로 알려 주세요.");
        return userService.toMyInfo(user);
    }

    private void validateNewEmail(User user, String email) {
        if (user.getEmail().equals(email)) {
            throw new BusinessException(UserErrorCode.SAME_EMAIL);
        }
        // 자기 자신은 빼고 본다. (대소문자만 바꾸는 것은 허용)
        if (userRepository.existsByEmailAndIdNot(email, user.getId())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_EMAIL);
        }
    }

    private void validateNewPhoneNumber(User user, String phoneNumber) {
        if (phoneNumber.equals(user.getPhoneNumber())) {
            throw new BusinessException(UserErrorCode.SAME_PHONE_NUMBER);
        }
        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new BusinessException(UserErrorCode.DUPLICATE_PHONE_NUMBER);
        }
    }

    /**
     * 재확인 토큰이 이 회원 것인지 먼저 확인하고(쓰지 않음), 새 값의 인증번호를 확인한 뒤 재확인 토큰을 쓴다.
     * 재확인이 만료됐는데 인증번호부터 확인하면 인증번호만 헛되이 쓰게 되므로 순서를 지킨다.
     * 인증번호는 확인하고 바로 토큰을 써서, 같은 인증으로 두 번 바꾸지 못하게 한다.
     */
    private void verifyWithReauth(Long userId, VerificationPurpose purpose, String target, String code, String reauthToken) {
        verificationService.requireMatchingToken(VerificationPurpose.REAUTH, reauthToken, reauthTarget(userId));
        String token = verificationService.verifyCode(purpose, target, code);
        verificationService.consumeToken(purpose, token);
        verificationService.consumeMatchingToken(VerificationPurpose.REAUTH, reauthToken, reauthTarget(userId));
    }

    // 재확인 인증번호·토큰의 대상. 회원마다 하나 (휴대폰 번호가 아니라 회원 기준이라 번호를 바꿔도 이어지지 않는다)
    private static String reauthTarget(Long userId) {
        return "user:" + userId;
    }

    // 이 UPDATE에서 바뀌는 유니크 컬럼은 하나뿐이라, 제약 위반이면 그 값의 중복으로 응답한다.
    private void flush(UserErrorCode duplicate) {
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(duplicate);
        }
    }

    // 변경이 커밋된 뒤에 안내 메일을 보낸다. (롤백되면 보내지 않음) 메일을 못 보내도 변경은 그대로 둔다.
    private void notifyAfterCommit(String to, String subject, String body) {
        Runnable send = () -> {
            try {
                emailSender.send(to, subject, body);
            } catch (RuntimeException e) {
                log.warn("연락처 변경 안내 메일을 보내지 못했습니다. to={}", maskEmail(to), e);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send.run();
            }
        });
    }
}
