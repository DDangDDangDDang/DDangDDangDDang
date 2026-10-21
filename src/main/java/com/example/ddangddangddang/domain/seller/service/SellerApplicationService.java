package com.example.ddangddangddang.domain.seller.service;

import com.example.ddangddangddang.domain.livechat.moderation.ChatContentFilter;
import com.example.ddangddangddang.domain.seller.dto.SellerApplicationRequest;
import com.example.ddangddangddang.domain.seller.dto.SellerApplicationResponse;
import com.example.ddangddangddang.domain.seller.entity.SellerApplication;
import com.example.ddangddangddang.domain.seller.entity.SellerApplicationStatus;
import com.example.ddangddangddang.domain.seller.exception.SellerErrorCode;
import com.example.ddangddangddang.domain.seller.repository.SellerApplicationRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final UserRepository userRepository;
    private final ChatContentFilter contentFilter;

    /**
     * 판매자 신청. 처음이면 새로 만들고, 반려된 신청이 있으면 고쳐 다시 낸다.
     * 심사 중이거나 이미 인증받았으면 신청할 수 없다.
     */
    @Transactional
    public SellerApplicationResponse apply(Long userId, SellerApplicationRequest request) {
        String storeName = request.storeName().strip();
        String introduction = request.introduction().strip();
        validateContent(storeName);
        validateContent(introduction);

        SellerApplication application = applicationRepository.findByUserId(userId).orElse(null);
        if (application != null) {
            application.resubmit(storeName, introduction);
            return SellerApplicationResponse.from(application);
        }
        try {
            return SellerApplicationResponse.from(
                    applicationRepository.saveAndFlush(SellerApplication.create(userId, storeName, introduction)));
        } catch (DataIntegrityViolationException e) {
            // 같은 회원이 동시에 두 번 신청한 경우
            throw new BusinessException(SellerErrorCode.APPLICATION_PENDING, e);
        }
    }

    /** 내 판매자 신청. 신청한 적이 없으면 null */
    public SellerApplicationResponse getMine(Long userId) {
        return applicationRepository.findByUserId(userId).map(SellerApplicationResponse::from).orElse(null);
    }

    /** 관리자 심사 목록. status가 없으면 전체, 최근 신청부터 */
    public CursorResponse<SellerApplicationResponse> getApplications(SellerApplicationStatus status, Long cursor, int size) {
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        List<SellerApplication> fetched = applicationRepository.findByCursor(status, cursor, PageRequest.of(0, pageSize + 1));
        Map<Long, User> users = userRepository.findAllById(fetched.stream().map(SellerApplication::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return CursorResponse.of(fetched, pageSize, application -> {
            User user = users.get(application.getUserId());
            return SellerApplicationResponse.of(application, user == null ? null : user.getNickname(), user == null ? null : user.getEmail());
        }, SellerApplication::getId);
    }

    @Transactional
    public SellerApplicationResponse approve(Long applicationId) {
        SellerApplication application = findById(applicationId);
        application.approve(LocalDateTime.now());
        return SellerApplicationResponse.from(application);
    }

    @Transactional
    public SellerApplicationResponse reject(Long applicationId, String reason) {
        SellerApplication application = findById(applicationId);
        application.reject(reason.strip(), LocalDateTime.now());
        return SellerApplicationResponse.from(application);
    }

    private SellerApplication findById(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(SellerErrorCode.APPLICATION_NOT_FOUND));
    }

    // 방송 정보처럼 연락처·외부 링크로 플랫폼 밖 거래를 유도하지 못하게 막는다.
    private void validateContent(String content) {
        contentFilter.findViolation(content).ifPresent(violation -> {
            throw new BusinessException(SellerErrorCode.CONTENT_NOT_ALLOWED, violation.getMessage());
        });
    }
}
