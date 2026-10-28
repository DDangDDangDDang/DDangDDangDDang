package com.example.ddangddangddang.domain.user.service;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일·휴대폰 번호 변경. 새 이메일·번호로 인증번호를 보내고, 맞게 입력하면 바꾼다.
 * 이메일은 로그인 아이디이고 휴대폰 번호는 이메일 찾기에 쓰므로, 본인 것인지 확인한 값만 저장한다.
 *
 * 인증번호는 새 값(이메일·번호) 기준으로 발급해 가입·비밀번호 재설정과 같은 재전송 대기·하루 제한을 받는다.
 * 중복은 먼저 조회로 확인하고, 두 회원이 동시에 같은 값으로 바꾸는 경우는 유니크 제약이 막는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserContactService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final VerificationService verificationService;
    private final EmailSender emailSender;
    private final SmsSender smsSender;

    public long sendEmailChangeCode(Long userId, String email) {
        validateNewEmail(userService.findUser(userId), email);
        String code = verificationService.issueCode(VerificationPurpose.CHANGE_EMAIL, email);
        emailSender.send(email, "[땅땅땅] 이메일 변경 인증번호",
                "이메일 변경 인증번호는 [" + code + "]입니다. 5분 안에 입력해 주세요.\n"
                        + "본인이 요청하지 않았다면 이 메일을 무시해 주세요.");
        return VerificationService.CODE_TTL.toSeconds();
    }

    /** 이메일을 바꾸고 새 이메일이 담긴 Access Token을 돌려준다. */
    @Transactional
    public MyInfoUpdateResponse changeEmail(Long userId, String email, String code) {
        User user = userService.findUser(userId);
        validateNewEmail(user, email);
        verify(VerificationPurpose.CHANGE_EMAIL, email, code);

        user.changeEmail(email);
        flush(UserErrorCode.DUPLICATE_EMAIL);
        return userService.withNewAccessToken(user);
    }

    public long sendPhoneNumberChangeCode(Long userId, String phoneNumber) {
        validateNewPhoneNumber(userService.findUser(userId), phoneNumber);
        String code = verificationService.issueCode(VerificationPurpose.CHANGE_PHONE, phoneNumber);
        smsSender.send(phoneNumber, "[땅땅땅] 휴대폰 번호 변경 인증번호 [" + code + "]를 입력해 주세요.");
        return VerificationService.CODE_TTL.toSeconds();
    }

    @Transactional
    public MyInfoResponse changePhoneNumber(Long userId, String phoneNumber, String code) {
        User user = userService.findUser(userId);
        validateNewPhoneNumber(user, phoneNumber);
        verify(VerificationPurpose.CHANGE_PHONE, phoneNumber, code);

        user.changePhoneNumber(phoneNumber);
        flush(UserErrorCode.DUPLICATE_PHONE_NUMBER);
        return userService.toMyInfo(user);
    }

    private void validateNewEmail(User user, String email) {
        if (user.getEmail().equals(email)) {
            throw new BusinessException(UserErrorCode.SAME_EMAIL);
        }
        if (userRepository.existsByEmail(email)) {
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

    // 인증번호를 확인하고 바로 토큰을 써서, 같은 인증으로 두 번 바꾸지 못하게 한다.
    private void verify(VerificationPurpose purpose, String target, String code) {
        String token = verificationService.verifyCode(purpose, target, code);
        verificationService.consumeToken(purpose, token);
    }

    // 이 UPDATE에서 바뀌는 유니크 컬럼은 하나뿐이라, 제약 위반이면 그 값의 중복으로 응답한다.
    private void flush(UserErrorCode duplicate) {
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(duplicate);
        }
    }
}
