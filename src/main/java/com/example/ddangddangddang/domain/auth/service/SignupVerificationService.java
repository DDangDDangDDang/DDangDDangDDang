package com.example.ddangddangddang.domain.auth.service;

import com.example.ddangddangddang.domain.auth.verification.VerificationPurpose;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.message.SmsSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 회원가입 전 휴대폰 본인 인증.
 * 가입할 때 휴대폰 번호가 본인 것인지 확인해 두어야, 나중에 휴대폰으로 이메일을 찾는 기능을 믿을 수 있다.
 */
@Service
@RequiredArgsConstructor
public class SignupVerificationService {

    private final VerificationService verificationService;
    private final UserRepository userRepository;
    private final SmsSender smsSender;

    /** 이미 가입된 번호면 인증번호를 보내기 전에 막는다. */
    public long sendCode(String phoneNumber) {
        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new BusinessException(UserErrorCode.DUPLICATE_PHONE_NUMBER);
        }
        String code = verificationService.issueCode(VerificationPurpose.SIGNUP_PHONE, phoneNumber);
        smsSender.send(phoneNumber, "[땅땅땅] 회원가입 인증번호 [" + code + "]를 입력해 주세요.");
        return VerificationService.CODE_TTL.toSeconds();
    }

    public String verifyCode(String phoneNumber, String code) {
        return verificationService.verifyCode(VerificationPurpose.SIGNUP_PHONE, phoneNumber, code);
    }
}