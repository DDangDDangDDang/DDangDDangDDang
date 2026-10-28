package com.example.ddangddangddang.domain.shop.controller;

import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.domain.donation.service.DonationPointService;
import com.example.ddangddangddang.global.security.jwt.JwtProvider;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 마일리지 상점: 닉네임 변경권 (3,000M), 닉네임 뱃지 (각 5,000M)
 * 결제와 닉네임 변경은 한 트랜잭션이라, 변경이 실패하면 마일리지도 빠지지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShopControllerTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    // 적립 묶음의 (order_id, source)는 유니크라 테스트마다 다른 주문 번호를 쓴다. (주문 테이블과 연관관계는 없음)
    private static final AtomicLong ORDER_SEQ = new AtomicLong(System.nanoTime() % 1_000_000_000L + 2_000_000_000L);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DonationPointService donationPointService;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    @MockitoBean
    private VerificationService verificationService;

    @Test
    @DisplayName("닉네임 변경권은 변경 제한 기간 중에만 살 수 있고, 사면 바로 닉네임이 바뀌며 30일 제한이 다시 시작된다")
    void nicknameChange() throws Exception {
        Member member = signupAndLogin();

        // 가입 후 첫 변경은 무료라 변경권이 필요 없다.
        items(member).andExpect(jsonPath("$.data[0].item").value("NICKNAME_CHANGE"))
                .andExpect(jsonPath("$.data[0].price").value(3_000))
                .andExpect(jsonPath("$.data[0].available").value(false))
                .andExpect(jsonPath("$.data[0].unavailableReason").value("지금은 변경권 없이 닉네임을 바꿀 수 있어요."));
        buyNicknameChange(member.bearer(), "변경권" + member.seq())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("USER_012"));

        // 무료로 한 번 바꾸면 30일 제한 → 변경권을 살 수 있다.
        String freeChanged = mockMvc.perform(patch("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, member.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\": \"무료" + member.seq() + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(freeChanged, "$.data.accessToken");
        items("Bearer " + token).andExpect(jsonPath("$.data[0].available").value(true));

        // 마일리지가 모자라면 바꾸지 않는다. (닉네임을 바꾼 뒤 잔액을 확인하므로 한 트랜잭션으로 함께 되돌린다)
        buyNicknameChange("Bearer " + token, "변경권" + member.seq())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("DONATION_002"));
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.nickname").value("무료" + member.seq()));

        earn(member, 3_500);
        String bought = buyNicknameChange("Bearer " + token, "변경권" + member.seq())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.nickname").value("변경권" + member.seq()))
                .andExpect(jsonPath("$.data.user.nicknameChangeAvailableAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String newToken = JsonPath.read(bought, "$.data.accessToken");
        assertThat(jwtProvider.parseClaims(newToken).get(JwtProvider.CLAIM_NICKNAME, String.class)).isEqualTo("변경권" + member.seq());

        wallet("Bearer " + newToken).andExpect(jsonPath("$.data.balance").value(500));
        mockMvc.perform(get("/api/v1/donation-points/histories").header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken))
                .andExpect(jsonPath("$.data.content[0].type").value("SHOP"))
                .andExpect(jsonPath("$.data.content[0].amount").value(3_000))
                .andExpect(jsonPath("$.data.content[0].balanceAfter").value(500));
    }

    @Test
    @DisplayName("닉네임을 바꿀 수 없으면(같은 닉네임, 다른 회원이 사용 중) 마일리지도 빠지지 않는다")
    void failedChangeKeepsMileage() throws Exception {
        Member member = signupAndLogin();
        Member other = signupAndLogin();
        String token = changeNicknameFree(member, "무료" + member.seq());
        earn(member, 3_000);

        buyNicknameChange("Bearer " + token, "무료" + member.seq())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("USER_013"));
        buyNicknameChange("Bearer " + token, other.nickname())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("USER_002"));
        buyNicknameChange("Bearer " + token, "가")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("COMMON_001"));

        wallet("Bearer " + token).andExpect(jsonPath("$.data.balance").value(3_000));
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.nickname").value("무료" + member.seq()));
    }

    @Test
    @DisplayName("닉네임 뱃지는 사면 영구로 가지고 바로 장착된다. 같은 뱃지는 다시 살 수 없고, 가진 뱃지끼리 바꿔 끼거나 뺄 수 있다")
    void badges() throws Exception {
        Member member = signupAndLogin();
        items(member).andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[4].item").value("BADGE_STAR"))
                .andExpect(jsonPath("$.data[4].badge").value("STAR"))
                .andExpect(jsonPath("$.data[4].price").value(5_000))
                .andExpect(jsonPath("$.data[4].available").value(true));

        // 마일리지가 모자라면 갖지 못한다.
        buyBadge(member, "STAR").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("DONATION_002"));
        myBadges(member).andExpect(jsonPath("$.data.owned.length()").value(0))
                .andExpect(jsonPath("$.data.equipped").doesNotExist());

        earn(member, 10_000);
        buyBadge(member, "STAR").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.owned[0]").value("STAR"))
                .andExpect(jsonPath("$.data.equipped").value("STAR"));
        // 이미 가진 뱃지는 다시 살 수 없고 마일리지도 빠지지 않는다.
        buyBadge(member, "STAR").andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("USER_014"));
        wallet(member.bearer()).andExpect(jsonPath("$.data.balance").value(5_000));
        items(member).andExpect(jsonPath("$.data[4].available").value(false))
                .andExpect(jsonPath("$.data[4].unavailableReason").value("이미 가지고 있어요. 내 뱃지에서 장착할 수 있어요."));

        buyBadge(member, "HALO").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.owned.length()").value(2))
                .andExpect(jsonPath("$.data.equipped").value("HALO"));
        wallet(member.bearer()).andExpect(jsonPath("$.data.balance").value(0));
        mockMvc.perform(get("/api/v1/donation-points/histories").header(HttpHeaders.AUTHORIZATION, member.bearer()))
                .andExpect(jsonPath("$.data.content[0].type").value("SHOP"))
                .andExpect(jsonPath("$.data.content[0].amount").value(5_000));

        // 가진 뱃지끼리 바꿔 끼기, 갖지 않은 뱃지는 안 됨, null이면 빼기
        equip(member, "\"STAR\"").andExpect(status().isOk()).andExpect(jsonPath("$.data.equipped").value("STAR"));
        equip(member, "\"DEVIL_WINGS\"").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("USER_015"));
        equip(member, "null").andExpect(status().isOk()).andExpect(jsonPath("$.data.equipped").doesNotExist());
        myBadges(member).andExpect(jsonPath("$.data.owned.length()").value(2))
                .andExpect(jsonPath("$.data.equipped").doesNotExist());
    }

    private ResultActions buyBadge(Member member, String badge) throws Exception {
        return mockMvc.perform(post("/api/v1/shop/badges/{badge}", badge).header(HttpHeaders.AUTHORIZATION, member.bearer()));
    }

    private ResultActions myBadges(Member member) throws Exception {
        return mockMvc.perform(get("/api/v1/users/me/badges").header(HttpHeaders.AUTHORIZATION, member.bearer())).andExpect(status().isOk());
    }

    private ResultActions equip(Member member, String badgeJson) throws Exception {
        return mockMvc.perform(put("/api/v1/users/me/badge").header(HttpHeaders.AUTHORIZATION, member.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"badge\": " + badgeJson + "}"));
    }

    private String changeNicknameFree(Member member, String nickname) throws Exception {
        String response = mockMvc.perform(patch("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, member.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\": \"" + nickname + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.accessToken");
    }

    /** 구매 확정 적립으로 마일리지를 넣는다. (주문당 최대 1,000M이라 나눠서) */
    private void earn(Member member, long amount) {
        long left = amount;
        while (left > 0) {
            long points = Math.min(1_000, left);
            donationPointService.earn(ORDER_SEQ.incrementAndGet(), member.id(), 999_999L, points * 100);
            left -= points;
        }
    }

    private ResultActions items(Member member) throws Exception {
        return items(member.bearer());
    }

    private ResultActions items(String bearer) throws Exception {
        return mockMvc.perform(get("/api/v1/shop/items").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk());
    }

    private ResultActions buyNicknameChange(String bearer, String nickname) throws Exception {
        return mockMvc.perform(post("/api/v1/shop/nickname-change").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\": \"" + nickname + "\"}"));
    }

    private ResultActions wallet(String bearer) throws Exception {
        return mockMvc.perform(get("/api/v1/donation-points/wallet").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk());
    }

    private Member signupAndLogin() throws Exception {
        int seq = SEQ.incrementAndGet();
        String suffix = UUID.randomUUID().toString().substring(0, 4);
        String email = "shop" + seq + "-" + suffix + "@test.com";
        String nickname = "상점" + seq + suffix;
        SignupFixture.stubPhoneVerification(verificationService);
        String signup = mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(SignupFixture.signupBody(email, "Abcd1234!", nickname)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(signup, "$.data.userId")).longValue();
        String login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"Abcd1234!\"}"))
                .andReturn().getResponse().getContentAsString();
        return new Member(id, seq + suffix.substring(0, 2), nickname, JsonPath.read(login, "$.data.accessToken"));
    }

    private record Member(Long id, String seq, String nickname, String accessToken) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }
}
