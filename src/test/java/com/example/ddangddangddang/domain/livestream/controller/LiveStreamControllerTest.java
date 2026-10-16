package com.example.ddangddangddang.domain.livestream.controller;

import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamCreateRequest;
import com.example.ddangddangddang.domain.livestream.infra.MediaServerClient;
import com.example.ddangddangddang.domain.livestream.repository.LiveViewerStatsRepository;
import com.example.ddangddangddang.domain.livestream.repository.LiveViewerStatsRepository.Ranked;
import com.example.ddangddangddang.domain.livestream.service.LiveStreamService;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 판매자당 종료되지 않은 방송 하나 규칙(실제 DB 유니크 제약)과 홈·내 방송 API를 확인한다.
 * 동시 요청이 각자 커밋되어야 해서 테스트 트랜잭션으로 감싸지 않고, 테스트마다 새 회원을 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LiveStreamControllerTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LiveStreamService liveStreamService;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    @MockitoBean
    private MediaServerClient mediaServerClient;

    // 시청자 수 순위(Redis)는 정해 둔 값을 돌려준다. 실제 Redis 동작은 LiveViewerStatsRepositoryTest에서 확인한다.
    @MockitoBean
    private LiveViewerStatsRepository statsRepository;

    @Test
    @DisplayName("종료되지 않은 방송이 있으면 새 방송을 만들 수 없고(409 LIVESTREAM_010), 종료하면 다시 만들 수 있다")
    void oneActiveStreamPerSeller() throws Exception {
        Member seller = signupAndLogin();
        Long first = longValue(create(seller, "첫 방송"), "$.data.id");

        mockMvc.perform(post("/api/v1/live-streams").header(HttpHeaders.AUTHORIZATION, seller.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"두 번째 방송\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LIVESTREAM_010"));

        mockMvc.perform(get("/api/v1/live-streams/me/active").header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(first))
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"));

        liveStreamService.end(first, seller.id());

        mockMvc.perform(get("/api/v1/live-streams/me/active").header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
        create(seller, "다시 만든 방송").andReturn();
        mockMvc.perform(get("/api/v1/live-streams/me/active").header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                .andExpect(jsonPath("$.data.title").value("다시 만든 방송"));
    }

    @Test
    @DisplayName("같은 판매자가 동시에 여러 번 만들어도 하나만 만들어지고 나머지는 LIVESTREAM_010이다 (DB 유니크 제약)")
    void concurrentCreate() throws Exception {
        Member seller = signupAndLogin();
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int n = i;
            results.add(executor.submit(() -> {
                ready.await();
                try {
                    liveStreamService.create(seller.id(), new LiveStreamCreateRequest("동시 방송 " + n, null));
                    return "OK";
                } catch (BusinessException e) {
                    return e.getErrorCode().getCode();
                }
            }));
        }
        ready.countDown();

        List<String> codes = new ArrayList<>();
        for (Future<String> result : results) {
            codes.add(result.get());
        }
        executor.shutdown();
        assertThat(codes).containsOnlyOnce("OK");
        assertThat(codes).filteredOn(code -> !code.equals("OK")).containsOnly("LIVESTREAM_010");
    }

    @Test
    @DisplayName("홈 화면은 시청자 많은 방송과 급증 방송을 판매자 닉네임·시청자 수와 함께 보여 준다")
    void home() throws Exception {
        Member seller = signupAndLogin();
        Long liveStreamId = longValue(create(seller, "홈 방송"), "$.data.id");
        given(mediaServerClient.createPublisherToken(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).willReturn("token");
        liveStreamService.start(liveStreamId, seller.id(), seller.nickname());
        given(statsRepository.topViewers(anyInt())).willReturn(List.of(new Ranked(liveStreamId, 42)));
        given(statsRepository.topSurging(anyInt())).willReturn(List.of(new Ranked(liveStreamId, 12)));
        given(statsRepository.getViewerCounts(anyCollection())).willReturn(Map.of(liveStreamId, 42));
        given(statsRepository.getIncreases(anyCollection())).willReturn(Map.of(liveStreamId, 12));

        mockMvc.perform(get("/api/v1/live-streams/home").param("size", "5")
                        .header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mostViewed[0].liveStreamId").value(liveStreamId))
                .andExpect(jsonPath("$.data.mostViewed[0].title").value("홈 방송"))
                .andExpect(jsonPath("$.data.mostViewed[0].sellerNickname").value(seller.nickname()))
                .andExpect(jsonPath("$.data.mostViewed[0].viewerCount").value(42))
                .andExpect(jsonPath("$.data.surging[0].liveStreamId").value(liveStreamId))
                .andExpect(jsonPath("$.data.surging[0].viewerIncrease").value(12));

        liveStreamService.end(liveStreamId, seller.id());
    }

    @Test
    @DisplayName("시작 예정 시간을 \"yyyy-MM-ddTHH:mm\"로 보내 방송을 만들면 홈의 방송 예정에 나오고, 지난 시간은 400 LIVESTREAM_012")
    void scheduledStream() throws Exception {
        Member seller = signupAndLogin();
        // 브라우저의 datetime-local 입력값처럼 초 없이 보낸다.
        String at = java.time.LocalDateTime.now().plusDays(2).withSecond(0).withNano(0)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));

        mockMvc.perform(post("/api/v1/live-streams").header(HttpHeaders.AUTHORIZATION, seller.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"예정 방송\", \"scheduledStartAt\": \"2020-01-01T10:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("LIVESTREAM_012"));

        Long liveStreamId = longValue(mockMvc.perform(post("/api/v1/live-streams")
                        .header(HttpHeaders.AUTHORIZATION, seller.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"예정 방송\", \"scheduledStartAt\": \"" + at + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.scheduledStartAt").value(at + ":00")), "$.data.id");

        mockMvc.perform(get("/api/v1/live-streams/home").header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upcoming[?(@.liveStreamId == " + liveStreamId + ")].scheduledStartAt").value(at + ":00"))
                .andExpect(jsonPath("$.data.upcoming[?(@.liveStreamId == " + liveStreamId + ")].sellerNickname").value(seller.nickname()));

        liveStreamService.end(liveStreamId, seller.id());
    }

    private org.springframework.test.web.servlet.ResultActions create(Member seller, String title) throws Exception {
        return mockMvc.perform(post("/api/v1/live-streams").header(HttpHeaders.AUTHORIZATION, seller.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"" + title + "\"}"))
                .andExpect(status().isCreated());
    }

    private Long longValue(org.springframework.test.web.servlet.ResultActions result, String path) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
    }

    private Member signupAndLogin() throws Exception {
        int seq = SEQ.incrementAndGet();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String email = "ls" + seq + "-" + suffix + "@test.com";
        String nickname = "판매자" + seq + suffix.substring(0, 4);
        String signup = mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"Abcd1234!\", \"nickname\": \"" + nickname + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(signup, "$.data.userId")).longValue();
        String login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"Abcd1234!\"}"))
                .andReturn().getResponse().getContentAsString();
        return new Member(id, nickname, JsonPath.read(login, "$.data.accessToken"));
    }

    private record Member(Long id, String nickname, String accessToken) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }
}
