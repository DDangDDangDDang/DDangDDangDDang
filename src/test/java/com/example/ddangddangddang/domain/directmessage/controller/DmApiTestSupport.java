package com.example.ddangddangddang.domain.directmessage.controller;

import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.domain.directmessage.repository.DmRateLimitRepository;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
import com.example.ddangddangddang.support.SignupFixture;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1:1 채팅 API 테스트 공통 설정.
 * <p>
 * 서비스가 저장을 TransactionTemplate으로 따로 커밋하므로 테스트 트랜잭션(@Transactional)으로 감싸지 않는다.
 * 대신 테스트마다 새 회원을 만들어 다른 테스트의 데이터와 섞이지 않게 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class DmApiTestSupport {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    protected TokenBlacklistRepository tokenBlacklistRepository;

    // 도배 제한(Redis)은 기본으로 통과(횟수 0)한다. 제한 테스트에서만 값을 바꾼다.
    @MockitoBean
    protected DmRateLimitRepository dmRateLimitRepository;

    // 실시간 전달은 누구에게 무엇을 보냈는지만 확인한다. 실제 WebSocket 전달은 DirectMessageWebSocketTest에서 확인한다.
    @MockitoBean
    protected StompBroadcaster stompBroadcaster;

    @MockitoBean
    protected VerificationService verificationService;

    protected Member alice;
    protected Member bob;

    @BeforeEach
    void setUpMembers() throws Exception {
        SignupFixture.stubPhoneVerification(verificationService);
        alice = signupAndLogin("앨리스");
        bob = signupAndLogin("밥");
    }

    protected ResultActions createRoom(Member member, Long targetUserId) throws Exception {
        return mockMvc.perform(post("/api/v1/dm/rooms")
                .header(HttpHeaders.AUTHORIZATION, member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUserId\": " + targetUserId + "}"));
    }

    protected Long roomId(Member member, Long targetUserId) throws Exception {
        return longValue(createRoom(member, targetUserId), "$.data.roomId");
    }

    protected ResultActions send(Member member, Long roomId, String clientMessageId, String content) throws Exception {
        String body = "{\"clientMessageId\": \"" + clientMessageId + "\", \"content\": \""
                + content.replace("\n", "\\n") + "\"}";
        return mockMvc.perform(post("/api/v1/dm/rooms/" + roomId + "/messages")
                .header(HttpHeaders.AUTHORIZATION, member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    protected Long sendAndGetId(Member member, Long roomId, String content) throws Exception {
        return longValue(send(member, roomId, UUID.randomUUID().toString(), content), "$.data.messageId");
    }

    protected ResultActions getRooms(Member member) throws Exception {
        return mockMvc.perform(get("/api/v1/dm/rooms").header(HttpHeaders.AUTHORIZATION, member.bearer()));
    }

    protected Long longValue(ResultActions result, String path) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
    }

    // 테스트마다 겹치지 않는 이메일·닉네임(최대 20자)으로 가입하고 로그인한다.
    protected Member signupAndLogin(String name) throws Exception {
        int seq = SEQ.incrementAndGet();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String email = "dm" + seq + "-" + suffix + "@test.com";
        String nickname = name + seq + suffix.substring(0, 4);
        String signup = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SignupFixture.signupBody(email, "Abcd1234!", nickname)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(signup, "$.data.userId")).longValue();

        String login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"Abcd1234!\"}"))
                .andReturn().getResponse().getContentAsString();
        return new Member(id, nickname, JsonPath.read(login, "$.data.accessToken"));
    }

    protected record Member(Long id, String nickname, String accessToken) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }
}
