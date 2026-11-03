package com.example.ddangddangddang.domain.address.controller;

import com.example.ddangddangddang.domain.auth.repository.LoginAttemptRepository;
import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.domain.livestream.infra.MediaServerClient;
import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 배송지: 저장·목록·기본 조회·수정·삭제·기본 설정, 첫 주문 배송지 자동 저장
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserAddressControllerTest {

    private static final String ADDRESSES = "/api/v1/users/me/addresses";
    private static final AtomicInteger SEQ = new AtomicInteger();
    // 주문의 경매 ID는 겹치면 안 되므로(유니크) 실행할 때마다 다른 큰 수에서 시작한다.
    private static final AtomicLong AUCTION_SEQ = new AtomicLong(System.currentTimeMillis());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private LoginAttemptRepository loginAttemptRepository;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    @MockitoBean
    private MediaServerClient mediaServerClient;

    @MockitoBean
    private VerificationService verificationService;

    @MockitoBean
    private StompBroadcaster stompBroadcaster;

    @Test
    @DisplayName("첫 배송지는 자동으로 기본이 되고, 목록은 기본이 맨 위·나머지는 최근 저장순이며, 수정할 수 있다")
    void createListUpdate() throws Exception {
        Member member = signupAndLogin();

        Long home = longValue(create(member, "집", "01011112222")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.addressName").value("집"))
                .andExpect(jsonPath("$.data.isDefault").value(true)), "$.data.addressId");
        Long office = longValue(create(member, "회사", "01033334444")
                .andExpect(jsonPath("$.data.isDefault").value(false)), "$.data.addressId");
        Long parents = longValue(create(member, "부모님 댁", "0312223333"), "$.data.addressId");

        // 목록: 집(기본) → 부모님 댁 → 회사
        mockMvc.perform(get(ADDRESSES).header(HttpHeaders.AUTHORIZATION, member.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].addressId").value(home))
                .andExpect(jsonPath("$.data[1].addressId").value(parents))
                .andExpect(jsonPath("$.data[2].addressId").value(office));

        // 기본 배송지
        mockMvc.perform(get(ADDRESSES + "/default").header(HttpHeaders.AUTHORIZATION, member.bearer()))
                .andExpect(jsonPath("$.data.addressId").value(home));

        // 수정: 빈 요청사항은 null로 저장되고, 기본 여부는 그대로
        mockMvc.perform(patch(ADDRESSES + "/{id}", office).header(HttpHeaders.AUTHORIZATION, member.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressBody("회사(새 건물)", "김철수", "01055556666", "서울시 마포구 월드컵로 2", "  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addressName").value("회사(새 건물)"))
                .andExpect(jsonPath("$.data.receiverName").value("김철수"))
                .andExpect(jsonPath("$.data.deliveryMemo").doesNotExist())
                .andExpect(jsonPath("$.data.isDefault").value(false));
    }

    @Test
    @DisplayName("기본을 바꾸면 기존 기본은 해제되고, 기본을 지우면 남은 것 중 가장 최근 것이 기본이 된다")
    void changeDefaultAndDelete() throws Exception {
        Member member = signupAndLogin();
        Long home = longValue(create(member, "집", "01011112222"), "$.data.addressId");
        Long office = longValue(create(member, "회사", "01033334444"), "$.data.addressId");
        Long parents = longValue(create(member, "부모님 댁", "0312223333"), "$.data.addressId");

        // 회사를 기본으로 → 집은 해제
        mockMvc.perform(patch(ADDRESSES + "/{id}/default", office).header(HttpHeaders.AUTHORIZATION, member.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true));
        mockMvc.perform(get(ADDRESSES).header(HttpHeaders.AUTHORIZATION, member.bearer()))
                .andExpect(jsonPath("$.data[0].addressId").value(office))
                .andExpect(jsonPath("$.data[1].isDefault").value(false))
                .andExpect(jsonPath("$.data[2].isDefault").value(false));

        // 기본(회사) 삭제 → 남은 것 중 가장 최근에 저장한 부모님 댁이 기본
        mockMvc.perform(delete(ADDRESSES + "/{id}", office).header(HttpHeaders.AUTHORIZATION, member.bearer()))
                .andExpect(status().isNoContent());
        defaultAddressIs(member, parents);

        // 기본(부모님 댁) 삭제 → 집이 기본
        mockMvc.perform(delete(ADDRESSES + "/{id}", parents).header(HttpHeaders.AUTHORIZATION, member.bearer()));
        defaultAddressIs(member, home);

        // 마지막 하나 삭제 → 기본 없음, 목록 비어 있음
        mockMvc.perform(delete(ADDRESSES + "/{id}", home).header(HttpHeaders.AUTHORIZATION, member.bearer()));
        mockMvc.perform(get(ADDRESSES + "/default").header(HttpHeaders.AUTHORIZATION, member.bearer()))
                .andExpect(jsonPath("$.data").doesNotExist());
        mockMvc.perform(get(ADDRESSES).header(HttpHeaders.AUTHORIZATION, member.bearer()))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("저장된 배송지가 없을 때 주문 배송지를 입력하면 기본 배송지로 저장되고, 다음 주문에서 다르게 입력해도 그대로다")
    void orderAutoSave() throws Exception {
        Member buyer = signupAndLogin();
        Member seller = signupAndLogin();

        registerOrderAddress(buyer, order(buyer, seller), "홍길동", "01012345678");
        mockMvc.perform(get(ADDRESSES + "/default").header(HttpHeaders.AUTHORIZATION, buyer.bearer()))
                .andExpect(jsonPath("$.data.addressName").value("기본 배송지"))
                .andExpect(jsonPath("$.data.receiverName").value("홍길동"))
                .andExpect(jsonPath("$.data.deliveryMemo").value("문 앞에 놔 주세요"))
                .andExpect(jsonPath("$.data.isDefault").value(true));

        // 두 번째 주문은 다른 사람이 받음 → 저장된 배송지는 그대로
        registerOrderAddress(buyer, order(buyer, seller), "김철수", "01098765432");
        mockMvc.perform(get(ADDRESSES).header(HttpHeaders.AUTHORIZATION, buyer.bearer()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].receiverName").value("홍길동"));
    }

    @Test
    @DisplayName("없으면 null·빈 목록, 10개 초과 ADDRESS_003, 남의 배송지 ADDRESS_002, 없는 배송지 ADDRESS_001, 검증 실패 400, 비로그인 401")
    void errors() throws Exception {
        Member owner = signupAndLogin();
        Member other = signupAndLogin();

        // 저장된 게 없으면 기본은 null, 목록은 빈 배열
        mockMvc.perform(get(ADDRESSES + "/default").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
        mockMvc.perform(get(ADDRESSES).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(jsonPath("$.data.length()").value(0));

        // 10개까지 저장, 11번째는 막힘
        Long first = longValue(create(owner, "배송지1", "01000000001"), "$.data.addressId");
        for (int number = 2; number <= 10; number++) {
            create(owner, "배송지" + number, "0100000000" + (number % 10)).andExpect(status().isCreated());
        }
        create(owner, "배송지11", "01000000011")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ADDRESS_003"));

        // 남의 배송지는 수정·삭제·기본 설정 불가
        mockMvc.perform(patch(ADDRESSES + "/{id}", first).header(HttpHeaders.AUTHORIZATION, other.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressBody("남이 고침", "남", "01099999999", "어딘가", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ADDRESS_002"));
        mockMvc.perform(delete(ADDRESSES + "/{id}", first).header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ADDRESS_002"));
        mockMvc.perform(patch(ADDRESSES + "/{id}/default", first).header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ADDRESS_002"));

        // 없는 배송지
        mockMvc.perform(delete(ADDRESSES + "/{id}", 999_999_999L).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ADDRESS_001"));

        // 검증 실패: 하이픈 넣은 연락처, 빈 이름
        create(other, "집", "010-1234-5678")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
        create(other, "   ", "01012345678")
                .andExpect(status().isBadRequest());

        // 로그인하지 않으면 401
        mockMvc.perform(get(ADDRESSES))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions create(Member member, String addressName, String receiverPhone) throws Exception {
        return mockMvc.perform(post(ADDRESSES).header(HttpHeaders.AUTHORIZATION, member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(addressBody(addressName, "홍길동", receiverPhone, "서울시 강남구 테헤란로 1", "문 앞에 놔 주세요")));
    }

    private String addressBody(String addressName, String receiverName, String receiverPhone,
                               String address, String deliveryMemo) {
        String memo = deliveryMemo == null ? "null" : "\"" + deliveryMemo + "\"";
        return """
                {"addressName": "%s", "receiverName": "%s", "receiverPhone": "%s", "address": "%s", "deliveryMemo": %s}
                """.formatted(addressName, receiverName, receiverPhone, address, memo);
    }

    private void defaultAddressIs(Member member, Long addressId) throws Exception {
        mockMvc.perform(get(ADDRESSES + "/default").header(HttpHeaders.AUTHORIZATION, member.bearer()))
                .andExpect(jsonPath("$.data.addressId").value(addressId))
                .andExpect(jsonPath("$.data.isDefault").value(true));
    }

    // 결제가 끝난 주문 (ESCROW): 배송지를 입력할 수 있는 상태
    private Long order(Member buyer, Member seller) {
        return orderRepository.save(Order.create(AUCTION_SEQ.incrementAndGet(), buyer.id(), seller.id(),
                "루기아 V", null, 50_000L)).getId();
    }

    private void registerOrderAddress(Member buyer, Long orderId, String receiverName, String receiverPhone)
            throws Exception {
        mockMvc.perform(patch("/api/v1/orders/{id}/address", orderId).header(HttpHeaders.AUTHORIZATION, buyer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"receiverName": "%s", "receiverPhone": "%s", "address": "서울시 강남구 테헤란로 1", "deliveryMemo": "문 앞에 놔 주세요"}
                                """.formatted(receiverName, receiverPhone)))
                .andExpect(status().isOk());
    }

    private Long longValue(ResultActions result, String path) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
    }

    private Member signupAndLogin() throws Exception {
        int seq = SEQ.incrementAndGet();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String email = "address" + seq + "-" + suffix + "@test.com";
        String nickname = "배송" + seq + suffix.substring(0, 4);
        SignupFixture.stubPhoneVerification(verificationService);
        String signup = mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(SignupFixture.signupBody(email, "Abcd1234!", nickname)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(signup, "$.data.userId")).longValue();
        String login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"Abcd1234!\"}"))
                .andReturn().getResponse().getContentAsString();
        return new Member(id, JsonPath.read(login, "$.data.accessToken"));
    }

    private record Member(Long id, String accessToken) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }
}