package com.example.ddangddangddang.domain.livestream.service;

import com.example.ddangddangddang.domain.livestream.dto.LiveStreamTokenResponse;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.exception.LiveStreamErrorCode;
import com.example.ddangddangddang.domain.livestream.infra.MediaServerClient;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class LiveStreamServiceTest {

    private static final Long LIVE_STREAM_ID = 10L;
    private static final Long SELLER_ID = 1L;

    @InjectMocks
    private LiveStreamService liveStreamService;

    @Mock
    private LiveStreamRepository liveStreamRepository;

    @Mock
    private MediaServerClient mediaServerClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Nested
    @DisplayName("송출 토큰 재발급")
    class ReissuePublisherToken {

        @Test
        @DisplayName("방송 중인 방송의 주인이면 같은 방에 송출할 수 있는 토큰을 발급하고, 방송 상태와 LiveKit 방은 건드리지 않는다")
        void success() {
            LiveStream liveStream = liveStream(true);
            given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream));
            given(mediaServerClient.createPublisherToken(liveStream.getRoomName(), "seller-1", "판매자"))
                    .willReturn("publisher-token");
            given(mediaServerClient.getWsUrl()).willReturn("ws://localhost:7880");

            LiveStreamTokenResponse response = liveStreamService.reissuePublisherToken(LIVE_STREAM_ID, SELLER_ID, "판매자");

            assertThat(response.liveStreamId()).isEqualTo(LIVE_STREAM_ID);
            assertThat(response.roomName()).isEqualTo(liveStream.getRoomName());
            assertThat(response.serverUrl()).isEqualTo("ws://localhost:7880");
            assertThat(response.token()).isEqualTo("publisher-token");
            then(mediaServerClient).should(never()).createRoom(anyString());
            then(liveStreamRepository).should(never()).saveAndFlush(liveStream);
        }

        @Test
        @DisplayName("방송 주인이 아니면 LIVESTREAM_002, 토큰을 발급하지 않는다")
        void notOwner() {
            given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream(true)));

            assertError(() -> liveStreamService.reissuePublisherToken(LIVE_STREAM_ID, 2L, "다른 사람"),
                    LiveStreamErrorCode.NOT_OWNER);
        }

        @Test
        @DisplayName("아직 시작하지 않은 방송이면 LIVESTREAM_004 (방송 시작으로 토큰을 받아야 함)")
        void scheduled() {
            given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream(false)));

            assertError(() -> liveStreamService.reissuePublisherToken(LIVE_STREAM_ID, SELLER_ID, "판매자"),
                    LiveStreamErrorCode.NOT_LIVE);
        }

        @Test
        @DisplayName("종료된 방송이면 LIVESTREAM_004")
        void ended() {
            LiveStream liveStream = liveStream(true);
            liveStream.end(SELLER_ID);
            given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream));

            assertError(() -> liveStreamService.reissuePublisherToken(LIVE_STREAM_ID, SELLER_ID, "판매자"),
                    LiveStreamErrorCode.NOT_LIVE);
        }

        @Test
        @DisplayName("없는 방송이면 LIVESTREAM_001")
        void notFound() {
            given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.empty());

            assertError(() -> liveStreamService.reissuePublisherToken(LIVE_STREAM_ID, SELLER_ID, "판매자"),
                    LiveStreamErrorCode.NOT_FOUND);
        }

        private void assertError(Runnable call, LiveStreamErrorCode errorCode) {
            assertThatThrownBy(call::run)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(errorCode);
            then(mediaServerClient).should(never()).createPublisherToken(anyString(), anyString(), anyString());
        }
    }

    private LiveStream liveStream(boolean live) {
        LiveStream liveStream = LiveStream.create(SELLER_ID, "테스트 방송");
        ReflectionTestUtils.setField(liveStream, "id", LIVE_STREAM_ID);
        if (live) {
            liveStream.start(SELLER_ID);
        }
        return liveStream;
    }
}
