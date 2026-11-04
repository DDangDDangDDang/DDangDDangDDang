package com.example.ddangddangddang.domain.notification.event;

import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.notification.repository.NotificationRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LiveStartedNotifierTest {

    private static final Long LIVE_STREAM_ID = 7L;
    private static final Long SELLER_ID = 3L;

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

    @Mock
    private NotificationRepository notificationRepository;

    private final LiveStream liveStream = mock(LiveStream.class);

    @BeforeEach
    void setUp() {
        given(liveStream.getSellerId()).willReturn(SELLER_ID);
        given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream));
    }

    @Test
    @DisplayName("최근 30분 안에 알린 적이 없으면, 이 방송으로 알렸다고 먼저 남기고 구독자에게 알린다")
    void notifies() {
        LocalDateTime before = LocalDateTime.now();
        given(liveStreamRepository.existsBySellerIdAndSubscribersNotifiedAtAfter(eq(SELLER_ID), any())).willReturn(false);
        given(subscriptionRepository.findSubscriberIds(SELLER_ID)).willReturn(List.of(10L, 11L));
        given(userRepository.findById(SELLER_ID)).willReturn(Optional.empty());

        notifier.notifySubscribers(LIVE_STREAM_ID);

        // 기준 시각은 지금에서 30분 전
        verify(liveStreamRepository).existsBySellerIdAndSubscribersNotifiedAtAfter(eq(SELLER_ID), argThat(since ->
                !since.isBefore(before.minus(LiveStartedNotifier.RENOTIFY_INTERVAL))
                        && !since.isAfter(LocalDateTime.now().minus(LiveStartedNotifier.RENOTIFY_INTERVAL))));
        var order = inOrder(liveStreamRepository, notificationDispatcher);
        order.verify(liveStreamRepository).markSubscribersNotified(eq(LIVE_STREAM_ID), any());
        order.verify(notificationDispatcher).dispatch(anyList());
    }

    @Test
    @DisplayName("30분 안에 이 판매자의 방송으로 이미 알렸으면 다시 알리지 않고, 이미 보낸 알림을 이 방송으로 옮긴다")
    void skipsWhenNotifiedRecently() {
        given(liveStreamRepository.existsBySellerIdAndSubscribersNotifiedAtAfter(eq(SELLER_ID), any())).willReturn(true);
        given(liveStream.getTitle()).willReturn("다시 켠 방송");

        notifier.notifySubscribers(LIVE_STREAM_ID);

        verify(notificationRepository).moveLiveStartedTo(eq(SELLER_ID), any(), eq(LIVE_STREAM_ID), eq("다시 켠 방송"), eq(false));

        verify(subscriptionRepository, never()).findSubscriberIds(any());
        verify(liveStreamRepository, never()).markSubscribersNotified(anyLong(), any());
        verify(notificationDispatcher, never()).dispatch(anyList());
    }

    @Test
    @DisplayName("구독자가 없으면 알렸다는 기록을 남기지 않는다 (나중에 구독자가 생겼을 때 막히지 않게)")
    void noSubscribers() {
        given(liveStreamRepository.existsBySellerIdAndSubscribersNotifiedAtAfter(eq(SELLER_ID), any())).willReturn(false);
        given(subscriptionRepository.findSubscriberIds(SELLER_ID)).willReturn(List.of());

        notifier.notifySubscribers(LIVE_STREAM_ID);

        verify(liveStreamRepository, never()).markSubscribersNotified(anyLong(), any());
    }
}
