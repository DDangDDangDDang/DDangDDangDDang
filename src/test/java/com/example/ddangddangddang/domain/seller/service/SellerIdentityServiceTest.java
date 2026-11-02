package com.example.ddangddangddang.domain.seller.service;

import com.example.ddangddangddang.domain.seller.dto.SellerIdentityResponse;
import com.example.ddangddangddang.domain.seller.entity.SellerIdentity;
import com.example.ddangddangddang.domain.seller.exception.SellerErrorCode;
import com.example.ddangddangddang.domain.seller.infra.IdentityVerifier;
import com.example.ddangddangddang.domain.seller.infra.VerifiedIdentity;
import com.example.ddangddangddang.domain.seller.repository.SellerIdentityRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SellerIdentityServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final String VERIFICATION_ID = "identity-1";
    private static final LocalDate ADULT_BIRTH = LocalDate.of(1990, 1, 1);

    @InjectMocks
    private SellerIdentityService identityService;

    @Mock
    private SellerIdentityRepository identityRepository;

    @Mock
    private IdentityVerifier identityVerifier;

    @Test
    @DisplayName("처음 인증하면 실명·생년월일과 CI 해시를 저장한다 (CI 원문은 저장하지 않는다)")
    void firstVerification() {
        given(identityVerifier.verify(VERIFICATION_ID)).willReturn(new VerifiedIdentity("홍길동", ADULT_BIRTH, "ci-1"));
        given(identityRepository.findByCiHash(SellerIdentity.hashCi("ci-1"))).willReturn(Optional.empty());
        given(identityRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(identityRepository.saveAndFlush(any())).willAnswer(invocation -> {
            SellerIdentity saved = invocation.getArgument(0);
            assertThat(saved.getCiHash()).isEqualTo(SellerIdentity.hashCi("ci-1")).doesNotContain("ci-1");
            return saved;
        });

        SellerIdentityResponse response = identityService.verify(USER_ID, VERIFICATION_ID);

        assertThat(response.realName()).isEqualTo("홍길동");
        assertThat(response.birthDate()).isEqualTo(ADULT_BIRTH);
    }

    @Test
    @DisplayName("만 19세 미만이면 SELLER_012로 막고 저장하지 않는다")
    void minorRejected() {
        LocalDate eighteen = LocalDate.now().minusYears(19).plusDays(1);
        given(identityVerifier.verify(VERIFICATION_ID)).willReturn(new VerifiedIdentity("김미성", eighteen, "ci-1"));

        assertThatThrownBy(() -> identityService.verify(USER_ID, VERIFICATION_ID))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(SellerErrorCode.IDENTITY_MINOR);
        then(identityRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("만 19세가 되는 날부터 판매자가 될 수 있다")
    void adultFromNineteenthBirthday() {
        LocalDate today = LocalDate.of(2026, 11, 2);

        assertThat(SellerIdentity.isAdult(LocalDate.of(2007, 11, 2), today)).isTrue();
        assertThat(SellerIdentity.isAdult(LocalDate.of(2007, 11, 3), today)).isFalse();
    }

    @Test
    @DisplayName("다른 계정이 이미 인증한 명의면 SELLER_013")
    void ciUsedByOtherAccount() {
        SellerIdentity other = SellerIdentity.create(OTHER_USER_ID, "홍길동", ADULT_BIRTH, SellerIdentity.hashCi("ci-1"),
                "identity-0", LocalDateTime.now());
        given(identityVerifier.verify(VERIFICATION_ID)).willReturn(new VerifiedIdentity("홍길동", ADULT_BIRTH, "ci-1"));
        given(identityRepository.findByCiHash(SellerIdentity.hashCi("ci-1"))).willReturn(Optional.of(other));

        assertThatThrownBy(() -> identityService.verify(USER_ID, VERIFICATION_ID))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(SellerErrorCode.IDENTITY_ALREADY_USED);
    }

    @Test
    @DisplayName("이미 인증한 계정이 다른 명의로 다시 인증하면 SELLER_014, 같은 명의면 이름을 새로 바꾼다")
    void reverification() {
        SellerIdentity mine = SellerIdentity.create(USER_ID, "홍길동", ADULT_BIRTH, SellerIdentity.hashCi("ci-1"),
                "identity-0", LocalDateTime.now());
        given(identityRepository.findByUserId(USER_ID)).willReturn(Optional.of(mine));

        given(identityVerifier.verify("identity-other")).willReturn(new VerifiedIdentity("김철수", ADULT_BIRTH, "ci-2"));
        given(identityRepository.findByCiHash(SellerIdentity.hashCi("ci-2"))).willReturn(Optional.empty());
        assertThatThrownBy(() -> identityService.verify(USER_ID, "identity-other"))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(SellerErrorCode.IDENTITY_MISMATCH);

        given(identityVerifier.verify("identity-renamed")).willReturn(new VerifiedIdentity("홍길순", ADULT_BIRTH, "ci-1"));
        given(identityRepository.findByCiHash(SellerIdentity.hashCi("ci-1"))).willReturn(Optional.of(mine));
        identityService.verify(USER_ID, "identity-renamed");

        assertThat(mine.getRealName()).isEqualTo("홍길순");
        assertThat(mine.getIdentityVerificationId()).isEqualTo("identity-renamed");
    }
}
