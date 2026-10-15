package com.example.ddangddangddang.domain.directmessage.controller;

import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.directmessage.dto.DmMessageResponse;
import com.example.ddangddangddang.domain.directmessage.dto.DmRoomResponse;
import com.example.ddangddangddang.domain.directmessage.repository.DmMessageRepository;
import com.example.ddangddangddang.domain.directmessage.service.DirectMessageService;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 서비스가 저장을 TransactionTemplate으로 따로 커밋하므로 테스트 트랜잭션(@Transactional)으로 감싸지 않는다.
 * 대신 테스트마다 새 회원을 만들어 다른 테스트의 데이터와 섞이지 않게 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DirectMessageControllerTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DirectMessageService directMessageService;

    @Autowired
    private DmMessageRepository dmMessageRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    // 실시간 전달은 누구에게 무엇을 보냈는지만 확인한다. 실제 WebSocket 전달은 DirectMessageWebSocketTest에서 확인한다.
    @MockitoBean
    private StompBroadcaster stompBroadcaster;

    private Member alice;
    private Member bob;

    @BeforeEach
    void setUp() throws Exception {
        alice = signupAndLogin("앨리스");
        bob = signupAndLogin("밥");
    }

    @Nested
    @DisplayName("POST /api/v1/dm/rooms")
    class GetOrCreateRoom {

        @Test
        @DisplayName("상대와의 방을 만들어 상대 정보와 함께 응답하고, 상대가 나에게 요청해도 같은 방을 준다")
        void sameRoomBothWays() throws Exception {
            createRoom(alice, bob.id())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.otherUser.userId").value(bob.id()))
                    .andExpect(jsonPath("$.data.otherUser.nickname").value(bob.nickname()))
                    .andExpect(jsonPath("$.data.lastMessageId").doesNotExist());
            Long roomId = roomId(alice, bob.id());

            assertThat(roomId(bob, alice.id())).isEqualTo(roomId);
            assertThat(roomId(alice, bob.id())).isEqualTo(roomId);
        }

        @Test
        @DisplayName("자기 자신과는 방을 만들 수 없다 (DM_003)")
        void self() throws Exception {
            createRoom(alice, alice.id())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("DM_003"));
        }

        @Test
        @DisplayName("없는 회원과는 방을 만들 수 없다 (DM_004)")
        void unknownTarget() throws Exception {
            createRoom(alice, 999_999L)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("DM_004"));
        }

        @Test
        @DisplayName("상대를 보내지 않으면 COMMON_001")
        void missingTarget() throws Exception {
            mockMvc.perform(post("/api/v1/dm/rooms")
                            .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_001"));
        }

        @Test
        @DisplayName("토큰 없이 요청하면 401")
        void noToken() throws Exception {
            mockMvc.perform(post("/api/v1/dm/rooms")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"targetUserId\": " + bob.id() + "}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("두 사람이 동시에 서로에게 방을 만들어도 방은 하나만 생긴다")
        void concurrentCreate() throws Exception {
            int threads = 8;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(1);
            List<Future<Long>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                boolean fromAlice = i % 2 == 0;
                Callable<Long> task = () -> {
                    ready.await();
                    DmRoomResponse room = fromAlice
                            ? directMessageService.getOrCreateRoom(alice.id(), bob.id())
                            : directMessageService.getOrCreateRoom(bob.id(), alice.id());
                    return room.roomId();
                };
                futures.add(executor.submit(task));
            }
            ready.countDown();

            Set<Long> roomIds = futures.stream().map(this::join).collect(Collectors.toSet());
            executor.shutdown();
            assertThat(roomIds).hasSize(1);
        }

        private Long join(Future<Long> future) {
            try {
                return future.get();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }

    @Nested
    @DisplayName("POST /api/v1/dm/rooms/{roomId}/messages")
    class Send {

        @Test
        @DisplayName("메시지를 저장해 201로 응답하고, 상대와 나에게 같은 메시지를 실시간으로 보낸다")
        void saveAndDeliver() throws Exception {
            Long roomId = roomId(alice, bob.id());

            send(alice, roomId, "c-1", "  안녕하세요, 사이즈 문의드려요  ")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.roomId").value(roomId))
                    .andExpect(jsonPath("$.data.senderId").value(alice.id()))
                    .andExpect(jsonPath("$.data.clientMessageId").value("c-1"))
                    .andExpect(jsonPath("$.data.content").value("안녕하세요, 사이즈 문의드려요"))
                    .andExpect(jsonPath("$.data.messageId").isNumber())
                    .andExpect(jsonPath("$.data.sentAt").isNotEmpty());

            ArgumentCaptor<Object> toBob = ArgumentCaptor.forClass(Object.class);
            then(stompBroadcaster).should().sendToUser(eq(bob.id()), eq("/queue/dm"), toBob.capture());
            ArgumentCaptor<Object> toAlice = ArgumentCaptor.forClass(Object.class);
            then(stompBroadcaster).should().sendToUser(eq(alice.id()), eq("/queue/dm"), toAlice.capture());
            DmMessageResponse delivered = (DmMessageResponse) toBob.getValue();
            assertThat(delivered.content()).isEqualTo("안녕하세요, 사이즈 문의드려요");
            assertThat(toAlice.getValue()).isEqualTo(delivered);
        }

        @Test
        @DisplayName("같은 clientMessageId로 다시 보내면 한 번만 저장하고 처음 메시지를 돌려주며, 실시간 전달은 다시 하지 않는다")
        void resendSameClientMessageId() throws Exception {
            Long roomId = roomId(alice, bob.id());
            String clientMessageId = UUID.randomUUID().toString();
            Number first = JsonPath.read(send(alice, roomId, clientMessageId, "입금했어요")
                    .andReturn().getResponse().getContentAsString(), "$.data.messageId");
            clearInvocations(stompBroadcaster);

            send(alice, roomId, clientMessageId, "입금했어요")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.messageId").value(first));

            then(stompBroadcaster).should(never()).sendToUser(anyLong(), anyString(), any());
            assertThat(dmMessageRepository.findAll().stream()
                    .filter(m -> clientMessageId.equals(m.getClientMessageId()))).hasSize(1);
        }

        @Test
        @DisplayName("다른 방에서 이미 쓴 clientMessageId면 DM_005")
        void clientMessageIdUsedInOtherRoom() throws Exception {
            Member carol = signupAndLogin("캐롤");
            Long withBob = roomId(alice, bob.id());
            Long withCarol = roomId(alice, carol.id());
            String clientMessageId = UUID.randomUUID().toString();
            send(alice, withBob, clientMessageId, "첫 번째");

            send(alice, withCarol, clientMessageId, "두 번째")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("DM_005"));
        }

        @Test
        @DisplayName("참여하지 않은 방이면 DM_002, 없는 방이면 DM_001")
        void notMemberOrNotFound() throws Exception {
            Member carol = signupAndLogin("캐롤");
            Long roomId = roomId(alice, bob.id());

            send(carol, roomId, "c-1", "끼어들기")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("DM_002"));
            send(alice, 999_999L, "c-2", "없는 방")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("DM_001"));
            then(stompBroadcaster).should(never()).sendToUser(anyLong(), anyString(), any());
        }

        @Test
        @DisplayName("공백만 있는 메시지(전각 공백 포함)는 COMMON_001")
        void blank() throws Exception {
            Long roomId = roomId(alice, bob.id());

            send(alice, roomId, "c-1", " ")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_001"));
            send(alice, roomId, "c-2", "  ")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_001"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/dm/rooms/{roomId}/messages")
    class GetMessages {

        @Test
        @DisplayName("최신 메시지부터 size개씩, nextCursor로 이전 메시지를 이어서 가져온다")
        void cursorPaging() throws Exception {
            Long roomId = roomId(alice, bob.id());
            for (int i = 1; i <= 5; i++) {
                send(i % 2 == 0 ? bob : alice, roomId, UUID.randomUUID().toString(), "메시지 " + i);
            }

            String page1 = getMessages(alice, roomId, null, 2)
                    .andExpect(jsonPath("$.data.content[0].content").value("메시지 5"))
                    .andExpect(jsonPath("$.data.content[1].content").value("메시지 4"))
                    .andExpect(jsonPath("$.data.hasNext").value(true))
                    .andReturn().getResponse().getContentAsString();
            Long cursor = ((Number) JsonPath.read(page1, "$.data.nextCursor")).longValue();

            String page2 = getMessages(bob, roomId, cursor, 2)
                    .andExpect(jsonPath("$.data.content[0].content").value("메시지 3"))
                    .andExpect(jsonPath("$.data.content[1].content").value("메시지 2"))
                    .andReturn().getResponse().getContentAsString();
            cursor = ((Number) JsonPath.read(page2, "$.data.nextCursor")).longValue();

            getMessages(alice, roomId, cursor, 2)
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.content[0].content").value("메시지 1"))
                    .andExpect(jsonPath("$.data.hasNext").value(false))
                    .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
        }

        @Test
        @DisplayName("참여하지 않은 방의 메시지는 볼 수 없다 (DM_002)")
        void notMember() throws Exception {
            Member carol = signupAndLogin("캐롤");
            Long roomId = roomId(alice, bob.id());

            getMessages(carol, roomId, null, 20)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("DM_002"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/dm/rooms")
    class GetRooms {

        @Test
        @DisplayName("메시지가 있는 내 방만 최근 대화순으로, 상대 정보와 마지막 메시지 미리보기와 함께 보여 준다")
        void myRooms() throws Exception {
            Member carol = signupAndLogin("캐롤");
            Member dave = signupAndLogin("데이브");
            Long withBob = roomId(alice, bob.id());
            Long withCarol = roomId(alice, carol.id());
            roomId(alice, dave.id()); // 메시지가 없는 방은 목록에 나오지 않는다.
            Long others = roomId(bob, carol.id()); // 내가 참여하지 않은 방

            send(alice, withBob, UUID.randomUUID().toString(), "밥에게 첫 메시지");
            send(carol, withCarol, UUID.randomUUID().toString(), "캐롤이 보낸\n두 줄 메시지");
            send(bob, others, UUID.randomUUID().toString(), "남의 대화");

            mockMvc.perform(get("/api/v1/dm/rooms").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(2))
                    .andExpect(jsonPath("$.data.content[0].roomId").value(withCarol))
                    .andExpect(jsonPath("$.data.content[0].otherUser.nickname").value(carol.nickname()))
                    .andExpect(jsonPath("$.data.content[0].lastMessagePreview").value("캐롤이 보낸 두 줄 메시지"))
                    .andExpect(jsonPath("$.data.content[1].roomId").value(withBob))
                    .andExpect(jsonPath("$.data.content[1].otherUser.userId").value(bob.id()))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }

        @Test
        @DisplayName("오래된 방에 새 메시지가 오면 맨 위로 올라오고, size와 nextCursor로 나눠 가져온다")
        void reorderAndPaging() throws Exception {
            Member carol = signupAndLogin("캐롤");
            Long withBob = roomId(alice, bob.id());
            Long withCarol = roomId(alice, carol.id());
            send(alice, withBob, UUID.randomUUID().toString(), "먼저 보낸 방");
            send(alice, withCarol, UUID.randomUUID().toString(), "나중에 보낸 방");
            send(bob, withBob, UUID.randomUUID().toString(), "다시 최신");

            String page1 = mockMvc.perform(get("/api/v1/dm/rooms").param("size", "1")
                            .header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                    .andExpect(jsonPath("$.data.content[0].roomId").value(withBob))
                    .andExpect(jsonPath("$.data.content[0].lastMessagePreview").value("다시 최신"))
                    .andExpect(jsonPath("$.data.hasNext").value(true))
                    .andReturn().getResponse().getContentAsString();
            String cursor = String.valueOf(((Number) JsonPath.read(page1, "$.data.nextCursor")).longValue());

            mockMvc.perform(get("/api/v1/dm/rooms").param("size", "1").param("cursor", cursor)
                            .header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                    .andExpect(jsonPath("$.data.content[0].roomId").value(withCarol))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }
    }

    private ResultActions createRoom(Member member, Long targetUserId) throws Exception {
        return mockMvc.perform(post("/api/v1/dm/rooms")
                .header(HttpHeaders.AUTHORIZATION, member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUserId\": " + targetUserId + "}"));
    }

    private Long roomId(Member member, Long targetUserId) throws Exception {
        String body = createRoom(member, targetUserId).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.roomId")).longValue();
    }

    private ResultActions send(Member member, Long roomId, String clientMessageId, String content) throws Exception {
        String body = "{\"clientMessageId\": \"" + clientMessageId + "\", \"content\": \""
                + content.replace("\n", "\\n") + "\"}";
        return mockMvc.perform(post("/api/v1/dm/rooms/" + roomId + "/messages")
                .header(HttpHeaders.AUTHORIZATION, member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions getMessages(Member member, Long roomId, Long cursor, int size) throws Exception {
        var request = get("/api/v1/dm/rooms/" + roomId + "/messages")
                .param("size", String.valueOf(size))
                .header(HttpHeaders.AUTHORIZATION, member.bearer());
        if (cursor != null) {
            request.param("cursor", String.valueOf(cursor));
        }
        return mockMvc.perform(request);
    }

    // 테스트마다 겹치지 않는 이메일·닉네임(최대 20자)으로 가입하고 로그인한다.
    private Member signupAndLogin(String name) throws Exception {
        int seq = SEQ.incrementAndGet();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String email = "dm" + seq + "-" + suffix + "@test.com";
        String nickname = name + seq + suffix.substring(0, 4);
        String signup = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"Abcd1234!\", \"nickname\": \"" + nickname + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(signup, "$.data.userId")).longValue();

        String login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
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
