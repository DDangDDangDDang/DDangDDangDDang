package com.example.ddangddangddang.domain.notification.event;

import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.notification.service.NotificationDispatcher;
import com.example.ddangddangddang.domain.seller.repository.SellerSubscriptionRepository;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LiveStartedNotifierTest {

    private static final Long LIVE_STREAM_ID = 7L;
    private static final Long SELLER_ID = 3L;
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 11, 4, 20, 0);

    @InjectMocks
    private LiveStartedNotifier notifier;

    @Mock
    private SellerSubscriptionRepository subscriptionRepository;

    @Mock
    private LiveStreamRepository liveStreamRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationDispatcher notificationDispatcher;

    private final LiveStream liveStream = mock(LiveStream.class);

    @BeforeEach
    void setUp() {
        given(liveStream.getId()).willReturn(LIVE_STREAM_ID);
        given(liveStream.getSellerId()).willReturn(SELLER_ID);
        given(liveStream.getStartedAt()).willReturn(STARTED_AT);
        given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream));
    }

    @Test
    @DisplayName("최근 30분 안에 시작한 다른 방송이 없으면 구독자에게 알린다")
    void notifies() {
        given(liveStreamRepository.existsBySellerIdAndIdNotAndStartedAtBetween(
                SELLER_ID, LIVE_STREAM_ID, STARTED_AT.minus(LiveStartedNotifier.RENOTIFY_INTERVAL), STARTED_AT)).willReturn(false);
        given(subscriptionRepository.findSubscriberIds(SELLER_ID)).willReturn(List.of(10L, 11L));
        given(userRepository.findById(SELLER_ID)).willReturn(Optional.empty());

        notifier.notifySubscribers(LIVE_STREAM_ID);

        verify(notificationDispatcher).dispatch(anyList());
    }

    @Test
    @DisplayName("판매자가 껐다 켜기를 반복해 30분 안에 시작한 다른 방송이 있으면 다시 알리지 않는다")
    void skipsWhenStartedRecently() {
        given(liveStreamRepository.existsBySellerIdAndIdNotAndStartedAtBetween(
                SELLER_ID, LIVE_STREAM_ID, STARTED_AT.minus(LiveStartedNotifier.RENOTIFY_INTERVAL), STARTED_AT)).willReturn(true);

        notifier.notifySubscribers(LIVE_STREAM_ID);

        verify(subscriptionRepository, never()).findSubscriberIds(any());
        verify(notificationDispatcher, never()).dispatch(anyList());
    }
}
