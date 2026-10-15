package com.example.ddangddangddang.domain.livestream.service;

import com.example.ddangddangddang.domain.livestream.dto.LiveStreamCard;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamHomeResponse;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.livestream.repository.LiveViewerStatsRepository;
import com.example.ddangddangddang.domain.livestream.repository.LiveViewerStatsRepository.Ranked;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LiveStreamHomeServiceTest {

    @InjectMocks
    private LiveStreamHomeService homeService;

    @Mock
    private LiveStreamRepository liveStreamRepository;

    @Mock
    private LiveViewerStatsRepository statsRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("시청자 많은 순과 급증 순으로 보여 주고, 순위에 남아 있는 끝난 방송은 뺀다")
    void rankingsWithoutEndedStreams() {
        LiveStream a = live(1L);
        LiveStream b = live(2L);
        LiveStream ended = live(3L);
        ended.end(103L);
        given(statsRepository.topViewers(anyInt())).willReturn(List.of(new Ranked(3L, 90), new Ranked(2L, 50), new Ranked(1L, 10)));
        given(statsRepository.topSurging(anyInt())).willReturn(List.of(new Ranked(1L, 8), new Ranked(3L, 5)));
        given(liveStreamRepository.findAllById(any())).willAnswer(invocation -> streams(invocation.getArgument(0), a, b, ended));
        given(liveStreamRepository.findByStatusOrderByStartedAtDesc(eq(LiveStreamStatus.LIVE), any())).willReturn(List.of(b, a));
        given(statsRepository.getViewerCounts(anyCollection())).willReturn(Map.of(1L, 10, 2L, 50));
        given(statsRepository.getIncreases(anyCollection())).willReturn(Map.of(1L, 8, 2L, -3));
        given(userRepository.findAllById(any())).willReturn(List.of(user(101L, "판매자A"), user(102L, "판매자B")));

        LiveStreamHomeResponse home = homeService.getHome(10);

        assertThat(home.mostViewed()).extracting(LiveStreamCard::liveStreamId).containsExactly(2L, 1L);
        assertThat(home.mostViewed().get(0).viewerCount()).isEqualTo(50);
        assertThat(home.mostViewed().get(0).sellerNickname()).isEqualTo("판매자B");
        assertThat(home.surging()).extracting(LiveStreamCard::liveStreamId).containsExactly(1L);
        assertThat(home.surging().get(0).viewerIncrease()).isEqualTo(8);
    }

    @Test
    @DisplayName("시청자 수가 아직 집계되지 않은 방송도 최근 시작한 순으로 채워, 시청자 0명으로 보여 준다")
    void fillWithRecentStreams() {
        LiveStream ranked = live(1L);
        LiveStream fresh = live(2L);
        given(statsRepository.topViewers(anyInt())).willReturn(List.of(new Ranked(1L, 7)));
        given(statsRepository.topSurging(anyInt())).willReturn(List.of());
        given(liveStreamRepository.findAllById(any())).willAnswer(invocation -> streams(invocation.getArgument(0), ranked, fresh));
        given(liveStreamRepository.findByStatusOrderByStartedAtDesc(eq(LiveStreamStatus.LIVE), any())).willReturn(List.of(fresh, ranked));
        given(statsRepository.getViewerCounts(anyCollection())).willReturn(Map.of(1L, 7));
        given(statsRepository.getIncreases(anyCollection())).willReturn(Map.of());
        given(userRepository.findAllById(any())).willReturn(List.of());

        LiveStreamHomeResponse home = homeService.getHome(10);

        assertThat(home.mostViewed()).extracting(LiveStreamCard::liveStreamId).containsExactly(1L, 2L);
        assertThat(home.mostViewed().get(1).viewerCount()).isZero();
        assertThat(home.surging()).isEmpty();
    }

    @Test
    @DisplayName("size만큼만 보여 준다")
    void limitSize() {
        LiveStream a = live(1L);
        LiveStream b = live(2L);
        given(statsRepository.topViewers(anyInt())).willReturn(List.of(new Ranked(1L, 9), new Ranked(2L, 5)));
        given(statsRepository.topSurging(anyInt())).willReturn(List.of());
        given(liveStreamRepository.findAllById(any())).willAnswer(invocation -> streams(invocation.getArgument(0), a, b));
        given(statsRepository.getViewerCounts(anyCollection())).willReturn(Map.of());
        given(statsRepository.getIncreases(anyCollection())).willReturn(Map.of());
        given(userRepository.findAllById(any())).willReturn(List.of());

        assertThat(homeService.getHome(1).mostViewed()).extracting(LiveStreamCard::liveStreamId).containsExactly(1L);
    }

    @Test
    @DisplayName("Redis를 읽지 못해도 실패하지 않고, 최근 시작한 방송을 시청자 수 0으로 보여 주며 급증 목록은 비운다")
    void redisFailure() {
        LiveStream a = live(1L);
        RedisConnectionFailureException down = new RedisConnectionFailureException("down");
        given(statsRepository.topViewers(anyInt())).willThrow(down);
        given(statsRepository.getViewerCounts(anyCollection())).willThrow(down);
        given(liveStreamRepository.findByStatusOrderByStartedAtDesc(eq(LiveStreamStatus.LIVE), any())).willReturn(List.of(a));
        given(userRepository.findAllById(any())).willReturn(List.of());

        LiveStreamHomeResponse home = homeService.getHome(10);

        assertThat(home.mostViewed()).extracting(LiveStreamCard::liveStreamId).containsExactly(1L);
        assertThat(home.mostViewed().get(0).viewerCount()).isZero();
        assertThat(home.surging()).isEmpty();
    }

    private static List<LiveStream> streams(Iterable<Long> ids, LiveStream... candidates) {
        Collection<Long> wanted = ids instanceof Collection<Long> c ? c : List.of();
        return java.util.Arrays.stream(candidates).filter(s -> wanted.contains(s.getId())).toList();
    }

    private static LiveStream live(Long id) {
        LiveStream liveStream = LiveStream.create(100L + id, "방송 " + id);
        ReflectionTestUtils.setField(liveStream, "id", id);
        liveStream.start(100L + id);
        return liveStream;
    }

    private static User user(Long id, String nickname) {
        User user = User.create("u" + id + "@test.com", "encoded", nickname);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
