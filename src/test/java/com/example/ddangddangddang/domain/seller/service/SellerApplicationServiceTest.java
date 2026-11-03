package com.example.ddangddangddang.domain.seller.service;

import com.example.ddangddangddang.domain.livechat.moderation.ChatContentFilter;
import com.example.ddangddangddang.domain.seller.dto.SellerApplicationRequest;
import com.example.ddangddangddang.domain.seller.entity.SellerApplication;
import com.example.ddangddangddang.domain.seller.entity.SellerApplicationStatus;
import com.example.ddangddangddang.domain.seller.entity.SellerIdentity;
import com.example.ddangddangddang.domain.seller.entity.SellerTerms;
import com.example.ddangddangddang.domain.seller.event.SellerApprovedEvent;
import com.example.ddangddangddang.domain.seller.exception.SellerErrorCode;
import com.example.ddangddangddang.domain.seller.repository.SellerApplicationRepository;
import com.example.ddangddangddang.domain.seller.repository.SellerIdentityRepository;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SellerApplicationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final SellerIdentity IDENTITY = SellerIdentity.create(USER_ID, "홍길동", LocalDate.of(1990, 1, 1),
            SellerIdentity.hashCi("ci"), "identity-1", LocalDateTime.of(2026, 1, 1, 0, 0));

    @InjectMocks
    private SellerApplicationService applicationService;

    @Mock
    private SellerApplicationRepository applicationRepository;

    @Mock
    private SellerIdentityRepository identityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChatContentFilter contentFilter;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("판매자 이용약관과 개인정보 수집에 모두 동의하지 않으면 요청 검증에서 막힌다")
    void termsAndPrivacyRequired() {
        Set<ConstraintViolation<SellerApplicationRequest>> violations =
                VALIDATOR.validate(request(false, false, SellerTerms.CURRENT_VERSION));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("termsAgreed", "privacyAgreed");
        assertThat(VALIDATOR.validate(request(true, true, SellerTerms.CURRENT_VERSION))).isEmpty();
    }

    @Test
    @DisplayName("동의 항목을 빼고 보내도 같은 동의 안내로 막힌다")
    void missingAgreementFields() {
        var missing = new SellerApplicationRequest("상점", "소개", null, "004", "123-456-789012", "홍길동",
                "서울시 중구 세종대로 110", "0212345678", null, null, SellerTerms.CURRENT_VERSION);

        assertThat(VALIDATOR.validate(missing)).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("termsAgreed", "privacyAgreed");
    }

    @Test
    @DisplayName("예전 약관 버전으로 낸 신청은 저장하지 않고 SELLER_009로 막는다")
    void outdatedTermsVersion() {
        assertThatThrownBy(() -> applicationService.apply(USER_ID, request(true, true, "2000-01-01")))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(SellerErrorCode.TERMS_OUTDATED);
        then(applicationRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("본인인증을 하지 않았으면 SELLER_010으로 막는다")
    void identityRequired() {
        given(identityRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.apply(USER_ID, request(true, true, SellerTerms.CURRENT_VERSION)))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(SellerErrorCode.IDENTITY_REQUIRED);
        then(applicationRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("처음 신청하면 동의한 약관 버전과 동의 시각이 함께 저장된다")
    void firstApplicationStoresAgreement() {
        given(identityRepository.findByUserId(USER_ID)).willReturn(Optional.of(IDENTITY));
        given(applicationRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(applicationRepository.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));

        var response = applicationService.apply(USER_ID, request(true, true, SellerTerms.CURRENT_VERSION));

        assertThat(response.termsVersion()).isEqualTo(SellerTerms.CURRENT_VERSION);
        assertThat(response.termsAgreedAt()).isNotNull();
        assertThat(response.status()).isEqualTo(SellerApplicationStatus.PENDING);
        // 정산 계좌는 하이픈을 빼고, 반품 주소·문의 연락처와 함께 저장된다.
        assertThat(response.bankCode()).isEqualTo("004");
        assertThat(response.accountNumber()).isEqualTo("123456789012");
        assertThat(response.accountHolder()).isEqualTo("홍길동");
        assertThat(response.returnAddress()).isEqualTo("서울시 중구 세종대로 110");
        assertThat(response.contactPhone()).isEqualTo("0212345678");
    }

    @Test
    @DisplayName("예금주가 본인인증 실명과 다르면 SELLER_016으로 막고, 띄어쓰기만 다르면 받는다")
    void accountHolderMustMatchIdentity() {
        given(identityRepository.findByUserId(USER_ID)).willReturn(Optional.of(IDENTITY));

        assertThatThrownBy(() -> applicationService.apply(USER_ID, request(true, true, SellerTerms.CURRENT_VERSION, "김철수")))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(SellerErrorCode.ACCOUNT_HOLDER_MISMATCH);
        then(applicationRepository).should(never()).saveAndFlush(any());

        given(applicationRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(applicationRepository.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));
        assertThat(applicationService.apply(USER_ID, request(true, true, SellerTerms.CURRENT_VERSION, "홍 길동")).accountHolder())
                .isEqualTo("홍 길동");
    }

    @Test
    @DisplayName("정산 계좌·반품 주소·문의 연락처 형식이 틀리면 요청 검증에서 막힌다")
    void sellerInfoValidation() {
        var invalid = new SellerApplicationRequest("상점", "소개", null, "4", "12-34", "", " ", "010-1234-5678",
                true, true, SellerTerms.CURRENT_VERSION);

        assertThat(VALIDATOR.validate(invalid)).extracting(v -> v.getPropertyPath().toString())
                .contains("bankCode", "accountNumber", "accountHolder", "returnAddress", "contactPhone");
    }

    @Test
    @DisplayName("반려 뒤 다시 신청하면 새로 동의한 약관 버전으로 바뀐다")
    void resubmitUpdatesAgreement() {
        SellerApplication rejected = SellerApplication.create(USER_ID, "상점", "소개");
        rejected.agreeTerms("2000-01-01", LocalDateTime.of(2026, 1, 1, 0, 0));
        rejected.reject("소개를 더 적어 주세요.", LocalDateTime.of(2026, 1, 2, 0, 0));
        given(identityRepository.findByUserId(USER_ID)).willReturn(Optional.of(IDENTITY));
        given(applicationRepository.findByUserId(USER_ID)).willReturn(Optional.of(rejected));

        applicationService.apply(USER_ID, request(true, true, SellerTerms.CURRENT_VERSION));

        assertThat(rejected.getTermsVersion()).isEqualTo(SellerTerms.CURRENT_VERSION);
        assertThat(rejected.getTermsAgreedAt()).isAfter(LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    @Test
    @DisplayName("승인하면 판매자 승인 축하 마일리지를 줄 수 있게 승인 이벤트를 낸다")
    void approvePublishesEvent() {
        SellerApplication pending = SellerApplication.create(USER_ID, "상점", "소개");
        given(applicationRepository.findById(10L)).willReturn(Optional.of(pending));

        applicationService.approve(10L);

        assertThat(pending.isApproved()).isTrue();
        then(eventPublisher).should().publishEvent(new SellerApprovedEvent(pending.getId(), USER_ID));
    }

    @Test
    @DisplayName("반려하면 승인 이벤트를 내지 않는다")
    void rejectDoesNotPublishEvent() {
        SellerApplication pending = SellerApplication.create(USER_ID, "상점", "소개");
        given(applicationRepository.findById(10L)).willReturn(Optional.of(pending));

        applicationService.reject(10L, "소개를 더 적어 주세요.");

        then(eventPublisher).should(never()).publishEvent(any());
    }

    private SellerApplicationRequest request(boolean termsAgreed, boolean privacyAgreed, String termsVersion) {
        return request(termsAgreed, privacyAgreed, termsVersion, "홍길동");
    }

    private SellerApplicationRequest request(boolean termsAgreed, boolean privacyAgreed, String termsVersion, String accountHolder) {
        return new SellerApplicationRequest("상점", "소개", null, "004", "123-456-789012", accountHolder,
                "서울시 중구 세종대로 110", "0212345678", termsAgreed, privacyAgreed, termsVersion);
    }
}
