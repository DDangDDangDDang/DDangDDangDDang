package com.example.ddangddangddang.domain.livestream.controller;

import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamCreateRequest;
import com.example.ddangddangddang.domain.livestream.infra.MediaServerClient;
import com.example.ddangddangddang.domain.livestream.repository.GuestJoinRateLimitRepository;
import com.example.ddangddangddang.domain.livestream.repository.LiveViewerStatsRepository;
import com.example.ddangddangddang.domain.livestream.repository.LiveViewerStatsRepository.Ranked;
import com.example.ddangddangddang.domain.livestream.service.LiveStreamService;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
import com.example.ddangddangddang.domain.seller.repository.SellerApplicationRepository;
import com.example.ddangddangddang.support.SellerFixture;
import com.example.ddangddangddang.support.SignupFixture;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    private SellerApplicationRepository sellerApplicationRepository;

    @Autowired
    private LiveStreamService liveStreamService;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    @MockitoBean
    private MediaServerClient mediaServerClient;

    @MockitoBean
    private VerificationService verificationService;

    // 손님 입장 횟수(Redis). 기본은 0이라 제한에 걸리지 않는다.
    @MockitoBean
    private GuestJoinRateLimitRepository guestJoinRateLimitRepository;

    // 시청자 수 순위(Redis)는 정해 둔 값을 돌려준다. 실제 Redis 동작은 LiveViewerStatsRepositoryTest에서 확인한다.
    @MockitoBean
    private LiveViewerStatsRepository statsRepository;

    @Test
    @DisplayName("판매자 인증을 받지 않은 회원은 방송을 만들 수 없다 (403 LIVESTREAM_016)")
    void notVerifiedSellerCannotCreate() throws Exception {
        Member member = signupAndLogin();
        sellerApplicationRepository.findByUserId(member.id()).ifPresent(sellerApplicationRepository::delete);

        mockMvc.perform(post("/api/v1/live-streams").header(HttpHeaders.AUTHORIZATION, member.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"인증 없는 방송\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("LIVESTREAM_016"));
        mockMvc.perform(get("/api/v1/live-streams/me/active").header(HttpHeaders.AUTHORIZATION, member.bearer()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

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
        SellerFixture.verify(sellerApplicationRepository, seller.id());

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

    @Test
    @DisplayName("로그인하지 않아도 방송·경매 조회와 시청 입장(손님)은 되고, 내 방송 조회·방송 만들기는 401이다")
    void guestBrowseAndJoin() throws Exception {
        Member seller = signupAndLogin();
        Long liveStreamId = longValue(create(seller, "손님 방송"), "$.data.id");
        given(mediaServerClient.createPublisherToken(anyString(), anyString(), anyString())).willReturn("token");
        given(mediaServerClient.createViewerToken(anyString(), anyString(), anyString())).willReturn("viewer-token");
        SellerFixture.verify(sellerApplicationRepository, seller.id());

        liveStreamService.start(liveStreamId, seller.id(), seller.nickname());

        // 둘러보기: 토큰 없이 조회
        mockMvc.perform(get("/api/v1/live-streams/" + liveStreamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("손님 방송"));
        mockMvc.perform(get("/api/v1/live-streams").param("status", "LIVE")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auctions")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/categories")).andExpect(status().isOk());

        // 내 정보·쓰기는 여전히 로그인 필요
        mockMvc.perform(get("/api/v1/live-streams/me/active"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_001"));
        mockMvc.perform(post("/api/v1/live-streams").contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"몰래\"}"))
                .andExpect(status().isUnauthorized());

        // 손님 입장: 접속마다 다른 손님 identity, 이름은 "손님"
        mockMvc.perform(post("/api/v1/live-streams/" + liveStreamId + "/join"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("viewer-token"));
        verify(mediaServerClient).createViewerToken(anyString(), startsWith("viewer-guest-"), eq("손님"));

        // 회원 입장은 지금처럼 회원 번호·닉네임으로
        Member viewer = signupAndLogin();
        mockMvc.perform(post("/api/v1/live-streams/" + liveStreamId + "/join").header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
                .andExpect(status().isOk());
        verify(mediaServerClient).createViewerToken(anyString(), eq("viewer-" + viewer.id()), eq(viewer.nickname()));
        // 손님 입장만 IP별로 센다. (회원 입장은 세지 않음)
        verify(guestJoinRateLimitRepository, times(1)).increase(anyString(), any());

        // 같은 IP의 손님 입장이 1분에 20회를 넘으면 방송 조회·토큰 발급 전에 429 LIVESTREAM_013으로 막는다.
        given(guestJoinRateLimitRepository.increase(anyString(), any())).willReturn(21L);
        mockMvc.perform(post("/api/v1/live-streams/" + liveStreamId + "/join"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("LIVESTREAM_013"));
        verify(mediaServerClient, times(1)).createViewerToken(anyString(), startsWith("viewer-guest-"), eq("손님"));

        liveStreamService.end(liveStreamId, seller.id());
    }

    @Test
    @DisplayName("송출이 없어 자동 종료하면(endForIdle) 방송이 끝나고, 판매자가 새 방송을 만들 수 있다")
    void endForIdle() throws Exception {
        Member seller = signupAndLogin();
        Long liveStreamId = longValue(create(seller, "자동 종료 방송"), "$.data.id");
        given(mediaServerClient.createPublisherToken(anyString(), anyString(), anyString())).willReturn("token");
        SellerFixture.verify(sellerApplicationRepository, seller.id());

        liveStreamService.start(liveStreamId, seller.id(), seller.nickname());

        liveStreamService.endForIdle(liveStreamId, seller.id());

        mockMvc.perform(get("/api/v1/live-streams/{liveStreamId}", liveStreamId))
                .andExpect(jsonPath("$.data.status").value("ENDED"));
        // 감시가 겹쳐 한 번 더 불러도 이미 끝난 방송은 다시 끝내지 않는다. (방 삭제·종료 안내 중복 방지)
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> liveStreamService.endForIdle(liveStreamId, seller.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(com.example.ddangddangddang.domain.livestream.exception.LiveStreamErrorCode.NOT_LIVE);
        verify(mediaServerClient, times(1)).deleteRoom(anyString());
        mockMvc.perform(get("/api/v1/live-streams/me/active").header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                .andExpect(jsonPath("$.data").doesNotExist());
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
        SignupFixture.stubPhoneVerification(verificationService);
        String signup = mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(SignupFixture.signupBody(email, "Abcd1234!", nickname)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(signup, "$.data.userId")).longValue();
        // 이 테스트의 회원은 모두 방송을 만드는 판매자라 인증해 둔다. (인증 전 막힘은 notVerifiedSellerCannotCreate에서 따로 본다)
        SellerFixture.verify(sellerApplicationRepository, id);
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
