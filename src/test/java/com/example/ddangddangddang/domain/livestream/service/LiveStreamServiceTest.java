package com.example.ddangddangddang.domain.livestream.service;

import com.example.ddangddangddang.domain.livechat.moderation.ChatContentFilter;
import com.example.ddangddangddang.domain.livechat.moderation.ContentViolation;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamCreateRequest;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamResponse;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamTokenResponse;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamUpdateRequest;
import com.example.ddangddangddang.domain.livestream.event.LiveStreamStartedEvent;
import com.example.ddangddangddang.domain.livestream.event.LiveStreamUpdatedEvent;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.livestream.exception.LiveStreamErrorCode;
import com.example.ddangddangddang.domain.livestream.infra.MediaServerClient;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.seller.service.SellerVerification;
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
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

    // 방송 시작은 판매자 인증을 받은 회원만 (기본은 인증 안 됨, 시작 테스트에서 인증으로 정한다)
    @Mock
    private SellerVerification sellerVerification;

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

    @Test
    @DisplayName("방송을 시작하면 LIVE가 된 방송 정보로 변경 이벤트와 시작 이벤트를 발행한다 (방송 예정 화면의 시청자에게 알림)")
    void startPublishesEvents() {
        LiveStream liveStream = liveStream(false);
        given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream));
        given(sellerVerification.isVerified(SELLER_ID)).willReturn(true);

        liveStreamService.start(LIVE_STREAM_ID, SELLER_ID, "판매자");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        then(eventPublisher).should(org.mockito.Mockito.times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues().get(0)).isInstanceOfSatisfying(LiveStreamUpdatedEvent.class,
                event -> assertThat(event.liveStream().status()).isEqualTo(LiveStreamStatus.LIVE));
        assertThat(captor.getAllValues().get(1)).isEqualTo(new LiveStreamStartedEvent(LIVE_STREAM_ID));
    }

    @Test
    @DisplayName("방송 서버에 방을 만들지 못하면 시작하지 않고 시청자에게 알리지도 않는다")
    void startFailsWithoutEvents() {
        given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream(false)));
        given(sellerVerification.isVerified(SELLER_ID)).willReturn(true);
        org.mockito.BDDMockito.willThrow(new BusinessException(LiveStreamErrorCode.MEDIA_SERVER_ERROR))
                .given(mediaServerClient).createRoom(anyString());

        assertThatThrownBy(() -> liveStreamService.start(LIVE_STREAM_ID, SELLER_ID, "판매자"))
                .isInstanceOf(BusinessException.class);
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("판매자 인증을 받지 않은 회원은 방송을 시작할 수 없다 (LIVESTREAM_016)")
    void startRequiresVerifiedSeller() {
        LiveStream liveStream = liveStream(false);
        given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream));

        assertThatThrownBy(() -> liveStreamService.start(LIVE_STREAM_ID, SELLER_ID, "판매자"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.NOT_VERIFIED_SELLER);
        assertThat(liveStream.getStatus()).isEqualTo(LiveStreamStatus.SCHEDULED);
        then(mediaServerClient).should(never()).createRoom(anyString());
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("남의 방송을 시작하려 하면 인증 여부와 상관없이 방송 주인 오류다 (LIVESTREAM_002)")
    void startOthersStream() {
        given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream(false)));

        assertThatThrownBy(() -> liveStreamService.start(LIVE_STREAM_ID, 2L, "다른 사람"))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.NOT_OWNER);
        then(sellerVerification).shouldHaveNoInteractions();
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
        @DisplayName("공지를 올리면 저장하고 변경 이벤트로 시청자 화면에 알린다. 빈 문자열이면 공지를 내린다")
        void notice() {
            LiveStream liveStream = liveStream(true);
            given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream));

            LiveStreamResponse posted = liveStreamService.update(LIVE_STREAM_ID, SELLER_ID,
                    new LiveStreamUpdateRequest(null, null, null, "  오늘 9시에 한정판 경매가 있어요  "));
            assertThat(posted.notice()).isEqualTo("오늘 9시에 한정판 경매가 있어요");
            assertThat(posted.title()).isEqualTo("테스트 방송");

            LiveStreamResponse cleared = liveStreamService.update(LIVE_STREAM_ID, SELLER_ID,
                    new LiveStreamUpdateRequest(null, null, null, ""));
            assertThat(cleared.notice()).isNull();
            then(liveStreamRepository).should(org.mockito.Mockito.times(2)).saveAndFlush(liveStream);
            then(eventPublisher).should(org.mockito.Mockito.times(2)).publishEvent(any(LiveStreamUpdatedEvent.class));
        }

        @Test
        @DisplayName("해시태그를 보내면 그 목록으로 바꾸고, 빈 목록이면 지우고, 보내지 않으면 그대로 둔다")
        void hashtags() {
            LiveStream liveStream = liveStream(true);
            given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream));

            LiveStreamResponse tagged = liveStreamService.update(LIVE_STREAM_ID, SELLER_ID,
                    new LiveStreamUpdateRequest(null, null, null, null, List.of("#나이키", "운동화")));
            assertThat(tagged.hashtags()).containsExactly("나이키", "운동화");

            LiveStreamResponse kept = liveStreamService.update(LIVE_STREAM_ID, SELLER_ID, new LiveStreamUpdateRequest("새 제목", null));
            assertThat(kept.hashtags()).containsExactly("나이키", "운동화");

            LiveStreamResponse cleared = liveStreamService.update(LIVE_STREAM_ID, SELLER_ID,
                    new LiveStreamUpdateRequest(null, null, null, null, List.of()));
            assertThat(cleared.hashtags()).isEmpty();
            then(eventPublisher).should(org.mockito.Mockito.times(3)).publishEvent(any(LiveStreamUpdatedEvent.class));
        }

        @Test
        @DisplayName("해시태그에도 연락처 같은 막힌 내용은 넣을 수 없다 (LIVESTREAM_009)")
        void hashtagContentNotAllowed() {
            given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream(true)));
            given(contentFilter.findViolation(any())).willAnswer(invocation -> "나이키 01012345678".equals(invocation.getArgument(0))
                    ? Optional.of(ContentViolation.PHONE_NUMBER) : Optional.empty());

            assertUpdateError(SELLER_ID, new LiveStreamUpdateRequest(null, null, null, null, List.of("나이키", "01012345678")),
                    LiveStreamErrorCode.CONTENT_NOT_ALLOWED);
        }

        @Test
        @DisplayName("공지에도 연락처 같은 막힌 내용은 넣을 수 없다 (LIVESTREAM_009)")
        void noticeContentNotAllowed() {
            given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream(true)));
            given(contentFilter.findViolation(any())).willAnswer(invocation -> "카톡 문의 010-1234-5678".equals(invocation.getArgument(0))
                    ? Optional.of(ContentViolation.PHONE_NUMBER) : Optional.empty());

            assertUpdateError(SELLER_ID, new LiveStreamUpdateRequest(null, null, null, "카톡 문의 010-1234-5678"), LiveStreamErrorCode.CONTENT_NOT_ALLOWED);
        }

        @Test
        @DisplayName("다른 사람의 방송에는 공지를 올릴 수 없다 (LIVESTREAM_002)")
        void noticeNotOwner() {
            LiveStream liveStream = liveStream(true);
            given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream));

            assertUpdateError(2L, new LiveStreamUpdateRequest(null, null, null, "공지"), LiveStreamErrorCode.NOT_OWNER);
            assertThat(liveStream.getNotice()).isNull();
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
    @DisplayName("방송 시작 예정 시간")
    class ScheduledStartAt {

        @Test
        @DisplayName("방송을 만들 때 시작 예정 시간을 정하면 함께 저장하고 응답한다")
        void createWithSchedule() {
            LocalDateTime at = LocalDateTime.now().plusDays(1).withNano(0);
            given(liveStreamRepository.existsByActiveSellerId(SELLER_ID)).willReturn(false);
            given(liveStreamRepository.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));

            LiveStreamResponse response = liveStreamService.create(SELLER_ID, new LiveStreamCreateRequest("예정 방송", null, at));

            assertThat(response.scheduledStartAt()).isEqualTo(at);
            assertThat(response.status()).isEqualTo(LiveStreamStatus.SCHEDULED);
        }

        @Test
        @DisplayName("지난 시간이나 30일보다 먼 시간은 LIVESTREAM_012로 거부하고 저장하지 않는다")
        void rejectOutOfRange() {
            assertScheduleError(LocalDateTime.now().minusMinutes(1), "방송 시작 예정 시간은 지금 이후로 정해 주세요.");
            assertScheduleError(LocalDateTime.now().plusDays(31), "방송 시작 예정 시간은 지금부터 30일 이내로 정해 주세요.");
            then(liveStreamRepository).should(never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("범위 경계: 지금과 정확히 30일 뒤는 허용하고, 정하지 않으면(null) 검사하지 않는다")
        void boundaries() {
            LocalDateTime now = LocalDateTime.of(2026, 10, 16, 12, 0);

            assertThatCode(() -> LiveStreamService.validateScheduledStartAt(now, now)).doesNotThrowAnyException();
            assertThatCode(() -> LiveStreamService.validateScheduledStartAt(now.plusDays(30), now)).doesNotThrowAnyException();
            assertThatCode(() -> LiveStreamService.validateScheduledStartAt(null, now)).doesNotThrowAnyException();
            assertThatThrownBy(() -> LiveStreamService.validateScheduledStartAt(now.plusDays(30).plusSeconds(1), now))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("대기 중인 방송은 시작 예정 시간을 바꿀 수 있고, 바뀌면 변경 이벤트를 보낸다")
        void changeWhileScheduled() {
            LiveStream liveStream = liveStream(false);
            given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream));
            LocalDateTime at = LocalDateTime.now().plusHours(3).withNano(0);

            LiveStreamResponse response = liveStreamService.update(LIVE_STREAM_ID, SELLER_ID,
                    new LiveStreamUpdateRequest(null, null, at));

            assertThat(response.scheduledStartAt()).isEqualTo(at);
            then(eventPublisher).should().publishEvent(any(LiveStreamUpdatedEvent.class));
        }

        @Test
        @DisplayName("이미 시작한 방송은 시작 예정 시간을 바꿀 수 없다 (LIVESTREAM_011). 다른 값만 바꾸는 요청은 그대로 된다")
        void rejectChangeAfterStart() {
            LiveStream liveStream = liveStream(true);
            given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream));

            assertThatThrownBy(() -> liveStreamService.update(LIVE_STREAM_ID, SELLER_ID,
                    new LiveStreamUpdateRequest(null, null, LocalDateTime.now().plusHours(1))))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(LiveStreamErrorCode.SCHEDULE_CHANGE_NOT_ALLOWED);

            assertThat(liveStreamService.update(LIVE_STREAM_ID, SELLER_ID,
                    new LiveStreamUpdateRequest("방송 중 제목 변경", null)).title()).isEqualTo("방송 중 제목 변경");
        }

        private void assertScheduleError(LocalDateTime at, String message) {
            assertThatThrownBy(() -> liveStreamService.create(SELLER_ID, new LiveStreamCreateRequest("예정 방송", null, at)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(message)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(LiveStreamErrorCode.INVALID_SCHEDULED_START);
        }
    }

    @Nested
    @DisplayName("판매자당 종료되지 않은 방송 하나")
    class ActiveStreamRule {

        @Test
        @DisplayName("종료되지 않은 방송이 있으면 새 방송을 만들 수 없다 (LIVESTREAM_010)")
        void rejectWhenActiveExists() {
            given(liveStreamRepository.existsByActiveSellerId(SELLER_ID)).willReturn(true);

            assertThatThrownBy(() -> liveStreamService.create(SELLER_ID, new LiveStreamCreateRequest("새 방송", null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(LiveStreamErrorCode.ACTIVE_STREAM_EXISTS);
            then(liveStreamRepository).should(never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("동시에 만들어 확인을 함께 통과해도, 저장 때 판매자당 하나 유니크 제약에 걸리면 LIVESTREAM_010 (MySQL처럼 테이블 이름이 붙어도)")
        void rejectOnActiveSellerUniqueViolation() {
            given(liveStreamRepository.existsByActiveSellerId(SELLER_ID)).willReturn(false);
            given(liveStreamRepository.saveAndFlush(any()))
                    .willThrow(violation("live_streams.uk_live_stream_active_seller"));

            assertThatThrownBy(() -> liveStreamService.create(SELLER_ID, new LiveStreamCreateRequest("새 방송", null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(LiveStreamErrorCode.ACTIVE_STREAM_EXISTS);
        }

        @Test
        @DisplayName("다른 제약 위반이나 제약 이름을 알 수 없는 위반은 LIVESTREAM_010으로 바꾸지 않고 원래 예외를 그대로 던진다")
        void rethrowOtherViolations() {
            given(liveStreamRepository.existsByActiveSellerId(SELLER_ID)).willReturn(false);
            DataIntegrityViolationException roomName = violation("live_streams.UK_room_name");
            DataIntegrityViolationException unknown = new DataIntegrityViolationException("not null");
            given(liveStreamRepository.saveAndFlush(any())).willThrow(roomName, unknown);

            assertThatThrownBy(() -> liveStreamService.create(SELLER_ID, new LiveStreamCreateRequest("새 방송", null)))
                    .isSameAs(roomName);
            assertThatThrownBy(() -> liveStreamService.create(SELLER_ID, new LiveStreamCreateRequest("새 방송", null)))
                    .isSameAs(unknown);
        }

        @Test
        @DisplayName("H2처럼 제약 이름이 대문자로 오거나 앞뒤에 다른 글자가 붙어도 판매자당 하나 제약으로 판별한다")
        void constraintNameMatching() {
            assertThat(LiveStreamService.isViolationOf(violation("PUBLIC.UK_LIVE_STREAM_ACTIVE_SELLER_INDEX_A"),
                    "uk_live_stream_active_seller")).isTrue();
            assertThat(LiveStreamService.isViolationOf(violation(null), "uk_live_stream_active_seller")).isFalse();
        }

        // Spring이 Hibernate 제약 위반을 감싸 주는 모양 그대로 만든다.
        private DataIntegrityViolationException violation(String constraintName) {
            return new DataIntegrityViolationException("could not execute statement",
                    new ConstraintViolationException("Duplicate entry", new SQLException("Duplicate entry", "23000", 1062), constraintName));
        }

        @Test
        @DisplayName("만든 방송은 종료되지 않은 방송으로 표시되고, 종료하면 표시가 사라진다")
        void activeUntilEnded() {
            LiveStream liveStream = liveStream(true);
            assertThat(liveStream.getActiveSellerId()).isEqualTo(SELLER_ID);

            liveStream.end(SELLER_ID);

            assertThat(liveStream.getActiveSellerId()).isNull();
        }

        @Test
        @DisplayName("내 종료되지 않은 방송을 조회하고, 없으면 null")
        void getMyActive() {
            given(liveStreamRepository.findByActiveSellerId(SELLER_ID)).willReturn(Optional.of(liveStream(false)));
            given(liveStreamRepository.findByActiveSellerId(2L)).willReturn(Optional.empty());

            assertThat(liveStreamService.getMyActive(SELLER_ID).id()).isEqualTo(LIVE_STREAM_ID);
            assertThat(liveStreamService.getMyActive(2L)).isNull();
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
