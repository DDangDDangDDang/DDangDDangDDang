package com.example.ddangddangddang.domain.seller.service;

import com.example.ddangddangddang.domain.livechat.moderation.ChatContentFilter;
import com.example.ddangddangddang.domain.seller.dto.SellerApplicationRequest;
import com.example.ddangddangddang.domain.seller.dto.SellerApplicationResponse;
import com.example.ddangddangddang.domain.seller.dto.SellerStoreUpdateRequest;
import com.example.ddangddangddang.domain.seller.entity.BusinessNumber;
import com.example.ddangddangddang.domain.seller.entity.SellerApplication;
import com.example.ddangddangddang.domain.seller.entity.SellerApplicationStatus;
import com.example.ddangddangddang.domain.seller.entity.SellerIdentity;
import com.example.ddangddangddang.domain.seller.entity.SellerTerms;
import com.example.ddangddangddang.domain.seller.exception.SellerErrorCode;
import com.example.ddangddangddang.domain.seller.repository.SellerApplicationRepository;
import com.example.ddangddangddang.domain.seller.repository.SellerIdentityRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 판매자 신청과 관리자 심사. 승인되면 판매자 인증 회원이 된다. (SellerVerification)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerApplicationService {

    static final int MAX_PAGE_SIZE = 50;

    private final SellerApplicationRepository applicationRepository;
    private final SellerIdentityRepository identityRepository;
    private final UserRepository userRepository;
    private final ChatContentFilter contentFilter;

    /**
     * 판매자 신청. 처음이면 새로 만들고, 반려된 신청이 있으면 고쳐 다시 낸다.
     * 심사 중이거나 이미 인증받았으면 신청할 수 없다.
     * 판매자 이용약관·개인정보 수집에 동의해야 하고(요청 검증), 동의한 버전이 지금 약관이어야 한다.
     */
    @Transactional
    public SellerApplicationResponse apply(Long userId, SellerApplicationRequest request) {
        if (!SellerTerms.isCurrent(request.termsVersion())) {
            throw new BusinessException(SellerErrorCode.TERMS_OUTDATED);
        }
        // 본인인증(만 19세 이상, 한 사람 한 계정)을 마친 회원만 신청할 수 있다. (SellerIdentityService)
        SellerIdentity identity = identityRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(SellerErrorCode.IDENTITY_REQUIRED));
        // 정산 계좌는 본인 명의만 받는다. (예금주 = 본인인증 실명)
        String accountHolder = request.accountHolder().strip();
        if (!identity.isAccountHolder(accountHolder)) {
            throw new BusinessException(SellerErrorCode.ACCOUNT_HOLDER_MISMATCH);
        }
        String accountNumber = normalizeAccountNumber(request.accountNumber());
        LocalDateTime now = LocalDateTime.now();
        String storeName = request.storeName().strip();
        String introduction = request.introduction().strip();
        String businessNumber = BusinessNumber.normalize(request.businessNumber());
        String returnAddress = request.returnAddress().strip();
        validateContent(storeName);
        validateContent(introduction);

        SellerApplication application = applicationRepository.findByUserId(userId).orElse(null);
        if (application != null) {
            application.resubmit(storeName, introduction, businessNumber);
            application.submitSellerInfo(request.bankCode(), accountNumber, accountHolder, returnAddress, request.contactPhone());
            application.agreeTerms(SellerTerms.CURRENT_VERSION, now);
            // 재신청이 겹치면(버튼 연타·다른 탭) 먼저 낸 신청만 남기고 나머지는 심사 중으로 안내한다.
            flush(application, SellerErrorCode.APPLICATION_PENDING);
            return SellerApplicationResponse.from(application);
        }
        SellerApplication created = SellerApplication.create(userId, storeName, introduction, businessNumber);
        created.submitSellerInfo(request.bankCode(), accountNumber, accountHolder, returnAddress, request.contactPhone());
        created.agreeTerms(SellerTerms.CURRENT_VERSION, now);
        try {
            return SellerApplicationResponse.from(applicationRepository.saveAndFlush(created));
        } catch (DataIntegrityViolationException e) {
            // 같은 회원이 동시에 두 번 신청한 경우
            throw new BusinessException(SellerErrorCode.APPLICATION_PENDING, e);
        }
    }

    /** 인증받은 판매자의 상점 정보 수정. 상점 이름·소개는 신청할 때와 같은 내용 검사를 한다. 반품 주소·문의 연락처는 빠지면 그대로 둔다. */
    @Transactional
    public SellerApplicationResponse updateStore(Long userId, SellerStoreUpdateRequest request) {
        String storeName = request.storeName().strip();
        String introduction = request.introduction().strip();
        validateContent(storeName);
        validateContent(introduction);
        String returnAddress = request.returnAddress() == null || request.returnAddress().isBlank() ? null : request.returnAddress().strip();

        SellerApplication application = applicationRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(SellerErrorCode.NOT_VERIFIED_SELLER));
        application.updateStore(storeName, introduction, returnAddress, request.contactPhone());
        // 관리자 심사와 겹칠 일은 없지만(승인된 신청만 바꿈), 같은 회원이 두 탭에서 동시에 고치면 먼저 저장한 쪽만 남긴다.
        flush(application, SellerErrorCode.ALREADY_REVIEWED);
        return SellerApplicationResponse.from(application);
    }

    /** 내 판매자 신청. 신청한 적이 없으면 null */
    public SellerApplicationResponse getMine(Long userId) {
        return applicationRepository.findByUserId(userId).map(SellerApplicationResponse::from).orElse(null);
    }

    /** 관리자 심사 목록. status가 없으면 전체, 최근 신청부터 */
    public CursorResponse<SellerApplicationResponse> getApplications(SellerApplicationStatus status, Long cursor, int size) {
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        List<SellerApplication> fetched = applicationRepository.findByCursor(status, cursor, PageRequest.of(0, pageSize + 1));
        List<Long> userIds = fetched.stream().map(SellerApplication::getUserId).toList();
        Map<Long, User> users = userRepository.findAllById(userIds)
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, SellerIdentity> identities = identityRepository.findAllByUserIdIn(userIds)
                .stream().collect(Collectors.toMap(SellerIdentity::getUserId, Function.identity()));
        return CursorResponse.of(fetched, pageSize, application -> {
            User user = users.get(application.getUserId());
            return SellerApplicationResponse.of(application, user == null ? null : user.getNickname(), user == null ? null : user.getEmail(),
                    identities.get(application.getUserId()));
        }, SellerApplication::getId);
    }

    @Transactional
    public SellerApplicationResponse approve(Long applicationId) {
        SellerApplication application = findById(applicationId);
        application.approve(LocalDateTime.now());
        flush(application, SellerErrorCode.ALREADY_REVIEWED);
        return SellerApplicationResponse.from(application);
    }

    @Transactional
    public SellerApplicationResponse reject(Long applicationId, String reason) {
        SellerApplication application = findById(applicationId);
        application.reject(reason.strip(), LocalDateTime.now());
        flush(application, SellerErrorCode.ALREADY_REVIEWED);
        return SellerApplicationResponse.from(application);
    }

    // 같은 신청을 동시에 바꾸면 먼저 커밋한 쪽만 성공한다. (@Version)
    private void flush(SellerApplication application, SellerErrorCode conflict) {
        try {
            applicationRepository.saveAndFlush(application);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(conflict, e);
        }
    }

    private SellerApplication findById(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(SellerErrorCode.APPLICATION_NOT_FOUND));
    }

    // 하이픈·공백을 뺀 숫자만 저장한다. (요청 검증은 숫자·하이픈 10~20자라 하이픈만 있는 값을 여기서 막는다)
    private static String normalizeAccountNumber(String accountNumber) {
        String digits = accountNumber.replaceAll("[\\s-]", "");
        if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) {
            throw new BusinessException(SellerErrorCode.INVALID_BANK_ACCOUNT);
        }
        return digits;
    }

    // 방송 정보처럼 연락처·외부 링크로 플랫폼 밖 거래를 유도하지 못하게 막는다.
    private void validateContent(String content) {
        contentFilter.findViolation(content).ifPresent(violation -> {
            throw new BusinessException(SellerErrorCode.CONTENT_NOT_ALLOWED, violation.getMessage());
        });
    }
}
