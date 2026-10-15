package com.example.ddangddangddang.domain.livestream.service;

import com.example.ddangddangddang.domain.livechat.moderation.ChatContentFilter;
import com.example.ddangddangddang.domain.livechat.moderation.ContentViolation;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamCreateRequest;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamResponse;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamTokenResponse;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamUpdateRequest;
import com.example.ddangddangddang.domain.livestream.event.LiveStreamUpdatedEvent;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.exception.LiveStreamErrorCode;
import com.example.ddangddangddang.domain.livestream.infra.MediaServerClient;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.exception.CommonErrorCode;
import com.example.ddangddangddang.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    // 기본은 막을 내용 없음(Optional.empty). 막는 경우만 따로 정한다.
    @Mock
    private ChatContentFilter contentFilter;

    @Test
    @DisplayName("방송을 만들 때도 전각 공백만으로 된 제목은 COMMON_001로 막고 저장하지 않는다")
    void createWithUnicodeBlankTitle() {
        assertThatThrownBy(() -> liveStreamService.create(SELLER_ID, new LiveStreamCreateRequest(" ", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("방송 제목은 비워 둘 수 없습니다.")
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
        then(liveStreamRepository).should(never()).save(any());
    }

    @Nested
    @DisplayName("방송 정보 수정")
    class Update {

        @Test
        @DisplayName("방송 중에 제목과 설명을 바꾸면 저장하고, 바뀐 방송 정보로 변경 이벤트를 발행한다")
        void updateWhileLive() {
            LiveStream liveStream = liveStream(true);
            given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream));

            LiveStreamResponse response = liveStreamService.update(LIVE_STREAM_ID, SELLER_ID,
                    new LiveStreamUpdateRequest("  새 제목  ", " 오늘은 신발 경매 "));

            assertThat(response.title()).isEqualTo("새 제목");
            assertThat(response.description()).isEqualTo("오늘은 신발 경매");
            then(liveStreamRepository).should().saveAndFlush(liveStream);
            ArgumentCaptor<LiveStreamUpdatedEvent> captor = ArgumentCaptor.forClass(LiveStreamUpdatedEvent.class);
            then(eventPublisher).should().publishEvent(captor.capture());
            assertThat(captor.getValue().liveStream()).isEqualTo(response);
        }

        @Test
        @DisplayName("대기 중인 방송도 바꿀 수 있고, 보내지 않은 값(null)은 그대로 둔다")
        void partialUpdateWhileScheduled() {
            LiveStream liveStream = LiveStream.create(SELLER_ID, "테스트 방송", "기존 설명");
            ReflectionTestUtils.setField(liveStream, "id", LIVE_STREAM_ID);
            given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream));

            LiveStreamResponse response = liveStreamService.update(LIVE_STREAM_ID, SELLER_ID,
                    new LiveStreamUpdateRequest("새 제목", null));

            assertThat(response.title()).isEqualTo("새 제목");
            assertThat(response.description()).isEqualTo("기존 설명");
        }

        @Test
        @DisplayName("설명을 빈 문자열로 보내면 설명을 지운다")
        void clearDescription() {
            LiveStream liveStream = LiveStream.create(SELLER_ID, "테스트 방송", "기존 설명");
            ReflectionTestUtils.setField(liveStream, "id", LIVE_STREAM_ID);
            given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream));

            LiveStreamResponse response = liveStreamService.update(LIVE_STREAM_ID, SELLER_ID,
                    new LiveStreamUpdateRequest(null, "  "));

            assertThat(response.description()).isNull();
            then(eventPublisher).should().publishEvent(any(LiveStreamUpdatedEvent.class));
        }

        @Test
        @DisplayName("바뀐 값이 없으면 저장하지 않고 이벤트도 발행하지 않는다")
        void nothingChanged() {
            given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream(true)));

            LiveStreamResponse response = liveStreamService.update(LIVE_STREAM_ID, SELLER_ID,
                    new LiveStreamUpdateRequest("테스트 방송", null));

            assertThat(response.title()).isEqualTo("테스트 방송");
            then(liveStreamRepository).should(never()).saveAndFlush(any());
            then(eventPublisher).should(never()).publishEvent(any());
        }

        @Test
        @DisplayName("방송 주인이 아니면 LIVESTREAM_002")
        void notOwner() {
            given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream(true)));

            assertUpdateError(2L, new LiveStreamUpdateRequest("새 제목", null), LiveStreamErrorCode.NOT_OWNER);
        }

        @Test
        @DisplayName("종료된 방송이면 LIVESTREAM_008")
        void ended() {
            LiveStream liveStream = liveStream(true);
            liveStream.end(SELLER_ID);
            given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream));

            assertUpdateError(SELLER_ID, new LiveStreamUpdateRequest("새 제목", null), LiveStreamErrorCode.ALREADY_ENDED);
        }

        @Test
        @DisplayName("제목이나 설명에 연락처 같은 막힌 내용이 있으면 이유를 담은 LIVESTREAM_009, 방송 정보는 그대로다")
        void contentNotAllowed() {
            LiveStream liveStream = liveStream(true);
            given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream));
            given(contentFilter.findViolation(any())).willAnswer(invocation -> "문의 010-1234-5678".equals(invocation.getArgument(0))
                    ? Optional.of(ContentViolation.PHONE_NUMBER) : Optional.empty());

            assertThatThrownBy(() -> liveStreamService.update(LIVE_STREAM_ID, SELLER_ID,
                    new LiveStreamUpdateRequest("새 제목", "문의 010-1234-5678")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("전화번호는 넣을 수 없습니다.")
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(LiveStreamErrorCode.CONTENT_NOT_ALLOWED);
            assertThat(liveStream.getTitle()).isEqualTo("테스트 방송");
            then(eventPublisher).should(never()).publishEvent(any());
        }

        @Test
        @DisplayName("없는 방송이면 LIVESTREAM_001")
        void notFound() {
            given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.empty());

            assertUpdateError(SELLER_ID, new LiveStreamUpdateRequest("새 제목", null), LiveStreamErrorCode.NOT_FOUND);
        }

        @ParameterizedTest
        @ValueSource(strings = {"  ", "\u001C", " \t "})
        @DisplayName("전각 공백 등 strip하면 비는 제목은 요청 검증을 통과해도 COMMON_001로 막고, 제목은 그대로다")
        void blankTitleAfterStrip(String title) {
            LiveStream liveStream = liveStream(true);
            given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream));

            assertUpdateError(SELLER_ID, new LiveStreamUpdateRequest(title, null), CommonErrorCode.INVALID_INPUT);
            assertThat(liveStream.getTitle()).isEqualTo("테스트 방송");
        }

        private void assertUpdateError(Long requesterId, LiveStreamUpdateRequest request, ErrorCode errorCode) {
            assertThatThrownBy(() -> liveStreamService.update(LIVE_STREAM_ID, requesterId, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(errorCode);
            then(liveStreamRepository).should(never()).saveAndFlush(any());
            then(eventPublisher).should(never()).publishEvent(any());
        }
    }

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
