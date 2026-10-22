package com.example.ddangddangddang.domain.seller.service;

import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.seller.dto.SubscribedSellerResponse;
import com.example.ddangddangddang.domain.seller.dto.SubscriptionResponse;
import com.example.ddangddangddang.domain.seller.entity.SellerSubscription;
import com.example.ddangddangddang.domain.seller.exception.SellerErrorCode;
import com.example.ddangddangddang.domain.seller.repository.SellerSubscriptionRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 판매자 구독. 구독한 판매자가 방송을 시작하면 알림을 받는다. (notification 도메인의 LiveStartedNotifier)
 * 판매자 인증 전인 회원도 방송을 할 수 있어서, 구독 대상은 자기 자신이 아닌 회원 누구나다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    // 홈 화면 구독 목록에 보여 줄 최대 판매자 수
    static final int MAX_SUBSCRIPTIONS_SHOWN = 100;

    private final SellerSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final LiveStreamRepository liveStreamRepository;
    private final SellerVerification sellerVerification;
    private final TransactionTemplate transactionTemplate;

    /** 구독한다. 이미 구독 중이면 그대로 둔다. (버튼 연타·다른 탭) */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SubscriptionResponse subscribe(Long subscriberId, Long sellerId) {
        if (subscriberId.equals(sellerId)) {
            throw new BusinessException(SellerErrorCode.CANNOT_SUBSCRIBE_SELF);
        }
        if (!userRepository.existsById(sellerId)) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        if (!subscriptionRepository.existsBySubscriberIdAndSellerId(subscriberId, sellerId)) {
            try {
                transactionTemplate.executeWithoutResult(status ->
                        subscriptionRepository.saveAndFlush(SellerSubscription.create(subscriberId, sellerId)));
            } catch (DataIntegrityViolationException e) {
                // 같은 구독 요청이 동시에 두 번 온 경우. 한쪽이 저장했으니 구독된 상태다.
            }
        }
        return new SubscriptionResponse(sellerId, true, subscriptionRepository.countBySellerId(sellerId));
    }

    /** 구독을 취소한다. 구독하지 않았어도 성공으로 본다. */
    @Transactional
    public SubscriptionResponse unsubscribe(Long subscriberId, Long sellerId) {
        subscriptionRepository.deleteBySubscriberIdAndSellerId(subscriberId, sellerId);
        return new SubscriptionResponse(sellerId, false, subscriptionRepository.countBySellerId(sellerId));
    }

    /** 내가 구독한 판매자. 방송 중인 판매자를 위에, 나머지는 최근 구독순 */
    public List<SubscribedSellerResponse> getMySubscriptions(Long subscriberId) {
        List<Long> sellerIds = subscriptionRepository
                .findBySubscriberIdOrderByIdDesc(subscriberId, PageRequest.of(0, MAX_SUBSCRIPTIONS_SHOWN))
                .stream().map(SellerSubscription::getSellerId).toList();
        if (sellerIds.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = userRepository.findAllById(sellerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, LiveStream> live = liveStreamRepository.findBySellerIdInAndStatus(sellerIds, LiveStreamStatus.LIVE).stream()
                .collect(Collectors.toMap(LiveStream::getSellerId, Function.identity(), (a, b) -> a));
        Set<Long> verified = sellerVerification.verifiedAmong(sellerIds);

        return sellerIds.stream()
                .map(id -> {
                    User user = users.get(id);
                    LiveStream stream = live.get(id);
                    return new SubscribedSellerResponse(id, user == null ? null : user.getNickname(), verified.contains(id),
                            stream == null ? null : stream.getId(), stream == null ? null : stream.getTitle());
                })
                // 정렬이 안정적이라 같은 그룹 안에서는 최근 구독순이 유지된다.
                .sorted(Comparator.comparing((SubscribedSellerResponse s) -> s.liveStreamId() == null))
                .toList();
    }

    public long countSubscribers(Long sellerId) {
        return subscriptionRepository.countBySellerId(sellerId);
    }

    public boolean isSubscribed(Long subscriberId, Long sellerId) {
        return subscriberId != null && subscriptionRepository.existsBySubscriberIdAndSellerId(subscriberId, sellerId);
    }
}
