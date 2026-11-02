package com.example.ddangddangddang.domain.seller.entity;

import com.example.ddangddangddang.domain.seller.exception.SellerErrorCode;
import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import com.example.ddangddangddang.global.exception.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 판매자 신청. 회원당 하나이고, 반려되면 같은 신청을 고쳐 다시 낸다.
 * 승인(APPROVED)된 회원은 판매자 인증 마크가 붙고 판매자 프로필을 보여 준다.
 * 신청할 때 정산 계좌(예금주 = 본인인증 실명)와 반품 주소·고객 문의 연락처를 함께 받는다.
 */
@Entity
@Table(
        name = "seller_applications",
        uniqueConstraints = @UniqueConstraint(name = "uk_seller_application_user", columnNames = "user_id"),
        indexes = @Index(name = "idx_seller_application_status", columnList = "status, id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerApplication extends BaseTimeEntity {

    public static final int STORE_NAME_MAX_LENGTH = 30;
    public static final int INTRODUCTION_MAX_LENGTH = 500;
    public static final int REJECT_REASON_MAX_LENGTH = 200;
    public static final int ACCOUNT_NUMBER_MAX_LENGTH = 20;
    public static final int ACCOUNT_HOLDER_MAX_LENGTH = 30;
    public static final int RETURN_ADDRESS_MAX_LENGTH = 200;
    public static final int CONTACT_PHONE_MAX_LENGTH = 11;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 상점 이름 (프로필에 닉네임과 함께 보여 줌)
    @Column(name = "store_name", nullable = false, length = STORE_NAME_MAX_LENGTH)
    private String storeName;

    // 판매자 소개 (어떤 상품을 파는지)
    @Column(nullable = false, length = INTRODUCTION_MAX_LENGTH)
    private String introduction;

    // 사업자 판매자의 사업자등록번호 (숫자 10자리). 개인 판매자는 null. 승인되면 프로필에 사업자 배지가 붙는다.
    @Column(name = "business_number", length = BusinessNumber.LENGTH)
    private String businessNumber;

    // 판매 대금 정산 계좌 (은행 코드 3자리, 하이픈을 뺀 계좌번호, 예금주). 예금주는 본인인증 실명과 같아야 한다.
    // 신청한 본인과 관리자만 본다. 정산 계좌를 받기 전에 낸 신청은 null
    @Column(name = "bank_code", length = 3)
    private String bankCode;

    @Column(name = "account_number", length = ACCOUNT_NUMBER_MAX_LENGTH)
    private String accountNumber;

    @Column(name = "account_holder", length = ACCOUNT_HOLDER_MAX_LENGTH)
    private String accountHolder;

    // 반품·교환 받을 주소와 고객 문의 연락처(하이픈 없는 숫자). 인증되면 판매자 프로필에 공개한다. 받기 전에 낸 신청은 null
    @Column(name = "return_address", length = RETURN_ADDRESS_MAX_LENGTH)
    private String returnAddress;

    @Column(name = "contact_phone", length = CONTACT_PHONE_MAX_LENGTH)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SellerApplicationStatus status;

    // 반려 사유. 반려됐을 때만 있다.
    @Column(name = "reject_reason", length = REJECT_REASON_MAX_LENGTH)
    private String rejectReason;

    // 신청할 때 동의한 판매자 이용약관·개인정보 수집 동의서 버전과 동의한 시각. 약관 동의를 받기 전에 낸 신청은 null
    @Column(name = "terms_version", length = SellerTerms.VERSION_MAX_LENGTH)
    private String termsVersion;

    @Column(name = "terms_agreed_at")
    private LocalDateTime termsAgreedAt;

    // 승인·반려한 시각. 승인된 회원은 이 시각이 판매자 인증일이다.
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    // 두 관리자가 같은 신청을 동시에 심사하거나, 재신청과 심사가 겹치면 한쪽만 성공시키기 위한 낙관적 락
    @Version
    private Long version;

    private SellerApplication(Long userId, String storeName, String introduction, String businessNumber) {
        this.userId = userId;
        this.storeName = storeName;
        this.introduction = introduction;
        this.businessNumber = businessNumber;
        this.status = SellerApplicationStatus.PENDING;
    }

    public static SellerApplication create(Long userId, String storeName, String introduction) {
        return create(userId, storeName, introduction, null);
    }

    /** businessNumber: BusinessNumber.normalize를 거친 값. 개인 판매자는 null */
    public static SellerApplication create(Long userId, String storeName, String introduction, String businessNumber) {
        return new SellerApplication(userId, storeName, introduction, businessNumber);
    }

    /** 반려된 신청을 고쳐 다시 낸다. 심사 중이거나 승인된 신청은 다시 낼 수 없다. */
    public void resubmit(String storeName, String introduction) {
        resubmit(storeName, introduction, null);
    }

    public void resubmit(String storeName, String introduction, String businessNumber) {
        if (status != SellerApplicationStatus.REJECTED) {
            throw new BusinessException(status == SellerApplicationStatus.APPROVED
                    ? SellerErrorCode.ALREADY_VERIFIED : SellerErrorCode.APPLICATION_PENDING);
        }
        this.storeName = storeName;
        this.introduction = introduction;
        this.businessNumber = businessNumber;
        this.status = SellerApplicationStatus.PENDING;
        this.rejectReason = null;
        this.reviewedAt = null;
    }

    /**
     * 인증받은 판매자의 상점 정보 수정. 다시 심사받지 않고 바로 바뀐다. (심사 대상인 사업자등록번호·정산 계좌는 바꿀 수 없다)
     * 반품 주소·문의 연락처는 null이면 그대로 둔다.
     */
    public void updateStore(String storeName, String introduction, String returnAddress, String contactPhone) {
        if (!isApproved()) {
            throw new BusinessException(SellerErrorCode.NOT_VERIFIED_SELLER);
        }
        this.storeName = storeName;
        this.introduction = introduction;
        if (returnAddress != null) {
            this.returnAddress = returnAddress;
        }
        if (contactPhone != null) {
            this.contactPhone = contactPhone;
        }
    }

    /**
     * 신청(재신청 포함)할 때 받는 정산 계좌와 반품 주소·문의 연락처.
     * accountNumber: 하이픈을 뺀 숫자, contactPhone: 하이픈 없는 숫자 (서비스에서 정리한 값)
     */
    public void submitSellerInfo(String bankCode, String accountNumber, String accountHolder,
                                 String returnAddress, String contactPhone) {
        this.bankCode = bankCode;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.returnAddress = returnAddress;
        this.contactPhone = contactPhone;
    }

    /** 신청(재신청 포함)할 때 동의한 약관 버전을 남긴다. 다시 신청하면 새로 동의한 버전으로 바뀐다. */
    public void agreeTerms(String version, LocalDateTime now) {
        this.termsVersion = version;
        this.termsAgreedAt = now;
    }

    public void approve(LocalDateTime now) {
        validatePending();
        this.status = SellerApplicationStatus.APPROVED;
        this.reviewedAt = now;
    }

    public void reject(String reason, LocalDateTime now) {
        validatePending();
        this.status = SellerApplicationStatus.REJECTED;
        this.rejectReason = reason;
        this.reviewedAt = now;
    }

    public boolean isApproved() {
        return status == SellerApplicationStatus.APPROVED;
    }

    /** 승인된 사업자 판매자 (프로필의 사업자 배지) */
    public boolean isApprovedBusiness() {
        return isApproved() && businessNumber != null;
    }

    private void validatePending() {
        if (status != SellerApplicationStatus.PENDING) {
            throw new BusinessException(SellerErrorCode.ALREADY_REVIEWED);
        }
    }
}
