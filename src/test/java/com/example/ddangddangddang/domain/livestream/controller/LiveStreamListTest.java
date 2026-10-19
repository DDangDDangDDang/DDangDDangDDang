package com.example.ddangddangddang.domain.livestream.controller;

import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.livestream.repository.LiveViewerStatsRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.security.jwt.JwtProvider;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/v1/live-streams (방송 목록·검색)를 실제 DB 쿼리로 확인한다.
 * 다른 테스트의 방송과 섞이지 않도록 테스트마다 고유한 단어(token)를 제목에 넣고 그 단어로 검색하거나, 새 판매자로 거른다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LiveStreamListTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 16, 12, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LiveStreamRepository liveStreamRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    @MockitoBean
    private LiveViewerStatsRepository statsRepository;

    private String token;
    private String bearer;

    @BeforeEach
    void setUp() {
        token = "zq" + UUID.randomUUID().toString().substring(0, 8);
        bearer = "Bearer " + jwtProvider.createAccessToken(seller("조회자"));
    }

    @Nested
    @DisplayName("상태별 정렬과 커서")
    class Ordering {

        @Test
        @DisplayName("방송 중(기본값)은 최근 시작순이고, 시작 시간이 같아도 커서로 빠짐없이 넘긴다")
        void liveOrder() throws Exception {
            Long first = live(token + " 1", BASE.plusMinutes(1));
            Long second = live(token + " 2", BASE.plusMinutes(2));
            Long sameAsSecond = live(token + " 2b", BASE.plusMinutes(2));
            Long third = live(token + " 3", BASE.plusMinutes(3));

            Long cursor = longValue(list(Map.of("keyword", token, "size", "2"))
                    .andExpect(jsonPath("$.data.content.length()").value(2))
                    .andExpect(jsonPath("$.data.content[0].liveStreamId").value(third))
                    .andExpect(jsonPath("$.data.content[1].liveStreamId").value(Math.max(second, sameAsSecond)))
                    .andExpect(jsonPath("$.data.hasNext").value(true)), "$.data.nextCursor");

            list(Map.of("keyword", token, "size", "2", "cursor", String.valueOf(cursor)))
                    .andExpect(jsonPath("$.data.content[0].liveStreamId").value(Math.min(second, sameAsSecond)))
                    .andExpect(jsonPath("$.data.content[1].liveStreamId").value(first))
                    .andExpect(jsonPath("$.data.hasNext").value(false))
                    .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
        }

        @Test
        @DisplayName("방송 예정은 시작 예정 시간이 가까운 순이고, 시작 예정 시간을 정하지 않은 방송은 나오지 않는다")
        void scheduledOrder() throws Exception {
            Long later = scheduled(token + " 나중", LocalDateTime.now().plusDays(2));
            Long sooner = scheduled(token + " 먼저", LocalDateTime.now().plusHours(1));
            scheduled(token + " 시간 없음", null);

            list(Map.of("status", "SCHEDULED", "keyword", token))
                    .andExpect(jsonPath("$.data.content.length()").value(2))
                    .andExpect(jsonPath("$.data.content[0].liveStreamId").value(sooner))
                    .andExpect(jsonPath("$.data.content[1].liveStreamId").value(later))
                    .andExpect(jsonPath("$.data.content[0].status").value("SCHEDULED"))
                    .andExpect(jsonPath("$.data.content[0].scheduledStartAt").isNotEmpty());
        }

        @Test
        @DisplayName("판매자의 종료된 방송은 최근 종료순으로, 다른 판매자의 방송 없이 나온다")
        void endedBySeller() throws Exception {
            User owner = seller("기록판매자");
            Long older = ended(owner, "지난 방송 1", BASE.plusHours(1));
            Long newer = ended(owner, "지난 방송 2", BASE.plusHours(3));
            Long middle = ended(owner, "지난 방송 3", BASE.plusHours(2));
            ended(seller("다른판매자"), "다른 사람 방송", BASE.plusHours(4));

            Long cursor = longValue(list(Map.of("status", "ENDED", "sellerId", String.valueOf(owner.getId()), "size", "2"))
                    .andExpect(jsonPath("$.data.content[0].liveStreamId").value(newer))
                    .andExpect(jsonPath("$.data.content[1].liveStreamId").value(middle))
                    .andExpect(jsonPath("$.data.content[0].endedAt").isNotEmpty())
                    .andExpect(jsonPath("$.data.content[0].sellerNickname").value(owner.getNickname())), "$.data.nextCursor");

            list(Map.of("status", "ENDED", "sellerId", String.valueOf(owner.getId()), "size", "2", "cursor", String.valueOf(cursor)))
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.content[0].liveStreamId").value(older));
        }
    }

    @Nested
    @DisplayName("넘겨 보는 사이 방송 상태가 바뀌어도 다음 페이지가 이어진다")
    class StatusChangedWhilePaging {

        @Test
        @DisplayName("방송 중 목록 1페이지의 마지막 방송이 그 사이 종료돼도, 그 방송을 커서로 다음 페이지를 가져온다")
        void liveStreamEndedBetweenPages() throws Exception {
            Long older = live(token + " 1", BASE.plusMinutes(1));
            Long newer = live(token + " 2", BASE.plusMinutes(2));
            Long cursor = longValue(list(Map.of("keyword", token, "size", "1"))
                    .andExpect(jsonPath("$.data.content[0].liveStreamId").value(newer)), "$.data.nextCursor");

            LiveStream ended = liveStreamRepository.findById(cursor).orElseThrow();
            ended.end(ended.getSellerId());
            liveStreamRepository.save(ended);

            list(Map.of("keyword", token, "size", "1", "cursor", String.valueOf(cursor)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].liveStreamId").value(older));
        }

        @Test
        @DisplayName("방송 예정 목록 1페이지의 마지막 방송이 그 사이 시작돼도, 그 방송을 커서로 다음 페이지를 가져온다")
        void scheduledStreamStartedBetweenPages() throws Exception {
            Long sooner = scheduled(token + " 먼저", LocalDateTime.now().plusHours(1));
            Long later = scheduled(token + " 나중", LocalDateTime.now().plusHours(2));
            Long cursor = longValue(list(Map.of("status", "SCHEDULED", "keyword", token, "size", "1"))
                    .andExpect(jsonPath("$.data.content[0].liveStreamId").value(sooner)), "$.data.nextCursor");

            LiveStream started = liveStreamRepository.findById(cursor).orElseThrow();
            started.start(started.getSellerId());
            liveStreamRepository.save(started);

            list(Map.of("status", "SCHEDULED", "keyword", token, "size", "1", "cursor", String.valueOf(cursor)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].liveStreamId").value(later));
        }
    }

    @Nested
    @DisplayName("검색")
    class Search {

        @Test
        @DisplayName("제목·설명·판매자 닉네임에서 대소문자 구분 없이 찾는다")
        void titleDescriptionNickname() throws Exception {
            Long byTitle = live("NIKE " + token.toUpperCase() + " 특가", BASE.plusMinutes(3));
            Long byDescription = live("아무 제목", "오늘은 " + token + " 신발", seller("판매자"), BASE.plusMinutes(2));
            Long byNickname = live("또 다른 제목", null, seller(token.substring(0, 10)), BASE.plusMinutes(1));
            live("관계없는 방송", BASE);

            list(Map.of("keyword", token))
                    .andExpect(jsonPath("$.data.content.length()").value(3))
                    .andExpect(jsonPath("$.data.content[0].liveStreamId").value(byTitle))
                    .andExpect(jsonPath("$.data.content[1].liveStreamId").value(byDescription))
                    .andExpect(jsonPath("$.data.content[2].liveStreamId").value(byNickname));
        }

        @Test
        @DisplayName("검색어의 %와 _는 글자 그대로 찾는다 (\"100%\"가 \"1000\"과 맞지 않음)")
        void escapeWildcards() throws Exception {
            Long percent = live(token + " 100% 정품", BASE.plusMinutes(2));
            live(token + " 1000원 특가", BASE.plusMinutes(1));
            Long underscore = live(token + " a_b 세트", BASE);
            live(token + " axb 세트", BASE.minusMinutes(1));

            list(Map.of("keyword", token + " 100%"))
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.content[0].liveStreamId").value(percent));
            list(Map.of("keyword", token + " a_b"))
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.content[0].liveStreamId").value(underscore));
        }

        @Test
        @DisplayName("방송 중인 방송 카드에는 시청자 수가 붙는다")
        void viewerCount() throws Exception {
            Long liveStreamId = live(token + " 시청자", BASE);
            given(statsRepository.getViewerCounts(anyCollection())).willReturn(Map.of(liveStreamId, 17));
            given(statsRepository.getIncreases(anyCollection())).willReturn(Map.of(liveStreamId, 4));

            list(Map.of("keyword", token))
                    .andExpect(jsonPath("$.data.content[0].viewerCount").value(17))
                    .andExpect(jsonPath("$.data.content[0].viewerIncrease").value(4))
                    .andExpect(jsonPath("$.data.content[0].status").value("LIVE"));
        }
    }

    @Nested
    @DisplayName("잘못된 요청")
    class InvalidRequest {

        @Test
        @DisplayName("50자를 넘는 검색어, 없는 커서, 없는 상태 값은 400 COMMON_001")
        void invalid() throws Exception {
            list(Map.of("keyword", "가".repeat(51)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_001"))
                    .andExpect(jsonPath("$.error.message").value("검색어는 50자 이하로 입력해 주세요."));
            list(Map.of("cursor", "99999999"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_001"));
            list(Map.of("status", "PAUSED"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("다른 상태 목록의 방송을 커서로 주면 (그 상태의 정렬 시간이 없으면) 400")
        void cursorFromOtherList() throws Exception {
            Long scheduledWithoutTime = scheduled(token, null);

            list(Map.of("status", "ENDED", "cursor", String.valueOf(scheduledWithoutTime)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_001"));
        }
    }

    private ResultActions list(Map<String, String> params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/live-streams").header(HttpHeaders.AUTHORIZATION, bearer);
        params.forEach(request::param);
        return mockMvc.perform(request);
    }

    private Long longValue(ResultActions result, String path) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
    }

    private Long live(String title, LocalDateTime startedAt) {
        return live(title, null, seller("판매자"), startedAt);
    }

    // 판매자당 종료되지 않은 방송은 하나라서 방송마다 새 판매자를 쓴다.
    private Long live(String title, String description, User seller, LocalDateTime startedAt) {
        LiveStream stream = LiveStream.create(seller.getId(), title, description);
        stream.start(seller.getId());
        ReflectionTestUtils.setField(stream, "startedAt", startedAt);
        return liveStreamRepository.save(stream).getId();
    }

    private Long scheduled(String title, LocalDateTime scheduledStartAt) {
        User seller = seller("예정판매자");
        return liveStreamRepository.save(LiveStream.create(seller.getId(), title, null, scheduledStartAt)).getId();
    }

    private Long ended(User seller, String title, LocalDateTime endedAt) {
        LiveStream stream = LiveStream.create(seller.getId(), title);
        stream.start(seller.getId());
        stream.end(seller.getId());
        ReflectionTestUtils.setField(stream, "endedAt", endedAt);
        return liveStreamRepository.save(stream).getId();
    }

    // 닉네임은 최대 20자, 겹치지 않게 뒤에 임의 글자를 붙인다.
    private User seller(String name) {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String nickname = name.length() > 13 ? name.substring(0, 13) : name;
        User user = User.create("list-" + suffix + "@test.com", "encoded", nickname + suffix);
        return userRepository.save(user);
    }
}
