package com.example.ddangddangddang.domain.seller.service;

import com.example.ddangddangddang.domain.seller.dto.SellerIdentityResponse;
import com.example.ddangddangddang.domain.seller.entity.SellerIdentity;
import com.example.ddangddangddang.domain.seller.exception.SellerErrorCode;
import com.example.ddangddangddang.domain.seller.infra.IdentityVerifier;
import com.example.ddangddangddang.domain.seller.infra.VerifiedIdentity;
import com.example.ddangddangddang.domain.seller.repository.SellerIdentityRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 판매자 신청 전 휴대폰 본인인증. 인증 결과를 서버가 직접 조회해 실명·생년월일을 남긴다.
 * 만 19세 미만은 받지 않고, 한 사람(CI)은 한 계정에서만 인증할 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerIdentityService {

    private final SellerIdentityRepository identityRepository;
    private final IdentityVerifier identityVerifier;

    @Transactional
    public SellerIdentityResponse verify(Long userId, String identityVerificationId) {
        VerifiedIdentity verified = identityVerifier.verify(identityVerificationId);
        LocalDateTime now = LocalDateTime.now();
        if (!SellerIdentity.isAdult(verified.birthDate(), now.toLocalDate())) {
            throw new BusinessException(SellerErrorCode.IDENTITY_MINOR);
        }
        String ciHash = SellerIdentity.hashCi(verified.ci());
        String name = verified.name().length() > SellerIdentity.NAME_MAX_LENGTH
                ? verified.name().substring(0, SellerIdentity.NAME_MAX_LENGTH) : verified.name();

        identityRepository.findByCiHash(ciHash)
                .filter(other -> !other.getUserId().equals(userId))
                .ifPresent(other -> {
                    throw new BusinessException(SellerErrorCode.IDENTITY_ALREADY_USED);
                });

        SellerIdentity identity = identityRepository.findByUserId(userId).orElse(null);
        if (identity != null) {
            // 이미 인증한 계정은 같은 사람만 다시 인증할 수 있다. (다른 사람 명의로 바꿔치기 방지)
            if (!identity.isSamePerson(ciHash)) {
                throw new BusinessException(SellerErrorCode.IDENTITY_MISMATCH);
            }
            identity.reverify(name, verified.birthDate(), identityVerificationId, now);
            return SellerIdentityResponse.from(identity);
        }
        try {
            return SellerIdentityResponse.from(identityRepository.saveAndFlush(
                    SellerIdentity.create(userId, name, verified.birthDate(), ciHash, identityVerificationId, now)));
        } catch (DataIntegrityViolationException e) {
            // 같은 명의로 두 계정이 동시에 인증했거나, 같은 회원이 동시에 두 번 인증한 경우
            throw new BusinessException(SellerErrorCode.IDENTITY_ALREADY_USED, e);
        }
    }

    /** 내 본인인증. 인증한 적이 없으면 null */
    public SellerIdentityResponse getMine(Long userId) {
        return identityRepository.findByUserId(userId).map(SellerIdentityResponse::from).orElse(null);
    }
}
