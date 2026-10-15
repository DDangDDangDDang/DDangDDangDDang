package com.example.ddangddangddang.domain.directmessage.controller;

import com.example.ddangddangddang.domain.directmessage.dto.DmEvent;
import com.example.ddangddangddang.domain.directmessage.dto.DmRoomResponse;
import com.example.ddangddangddang.domain.directmessage.repository.DmMessageRepository;
import com.example.ddangddangddang.domain.directmessage.service.DirectMessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1단계(대화방, 메시지, 실시간 전달)와 2단계(읽음, 읽지 않은 수, 내용 필터, 도배 제한) API 테스트
 */
class DirectMessageControllerTest extends DmApiTestSupport {

    @Autowired
    private DirectMessageService directMessageService;

    @Autowired
    private DmMessageRepository dmMessageRepository;

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
                    .andExpect(jsonPath("$.data.lastMessageId").doesNotExist())
                    .andExpect(jsonPath("$.data.unreadCount").value(0))
                    .andExpect(jsonPath("$.data.blockedByMe").value(false));
            Long roomId = roomId(alice, bob.id());

            assertThat(roomId(bob, alice.id())).isEqualTo(roomId);
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
        @DisplayName("메시지를 저장해 201로 응답하고, 상대와 나에게 같은 MESSAGE 이벤트를 실시간으로 보낸다")
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

            DmEvent toBob = captureEvent(bob.id());
            DmEvent toAlice = captureEvent(alice.id());
            assertThat(toBob.type()).isEqualTo(DmEvent.Type.MESSAGE);
            assertThat(toBob.roomId()).isEqualTo(roomId);
            assertThat(toBob.message().content()).isEqualTo("안녕하세요, 사이즈 문의드려요");
            assertThat(toAlice).isEqualTo(toBob);
        }

        @Test
        @DisplayName("같은 clientMessageId로 다시 보내면 한 번만 저장하고, 실시간 전달과 도배 횟수 세기를 다시 하지 않는다")
        void resendSameClientMessageId() throws Exception {
            Long roomId = roomId(alice, bob.id());
            String clientMessageId = UUID.randomUUID().toString();
            Long first = longValue(send(alice, roomId, clientMessageId, "입금했어요"), "$.data.messageId");
            clearInvocations(stompBroadcaster, dmRateLimitRepository);

            send(alice, roomId, clientMessageId, "입금했어요")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.messageId").value(first));

            then(stompBroadcaster).should(never()).sendToUser(anyLong(), anyString(), any());
            then(dmRateLimitRepository).should(never()).increment(anyLong(), any());
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

        @Test
        @DisplayName("전화번호·외부 링크 등 막힌 내용은 이유를 담은 DM_006, 저장하지 않는다")
        void contentNotAllowed() throws Exception {
            Long roomId = roomId(alice, bob.id());
            String clientMessageId = UUID.randomUUID().toString();

            send(alice, roomId, clientMessageId, "직거래해요 010 1234 5678")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("DM_006"))
                    .andExpect(jsonPath("$.error.message").value("전화번호는 넣을 수 없습니다."));
            send(alice, roomId, UUID.randomUUID().toString(), "open.kakao.com/o/abc 로 와요")
                    .andExpect(jsonPath("$.error.code").value("DM_006"));

            assertThat(dmMessageRepository.findBySenderIdAndClientMessageId(alice.id(), clientMessageId)).isEmpty();
            then(stompBroadcaster).should(never()).sendToUser(anyLong(), anyString(), any());
        }

        @Test
        @DisplayName("10초에 10개를 넘게 보내면 DM_007(429), 저장하지 않는다")
        void tooManyMessages() throws Exception {
            given(dmRateLimitRepository.increment(eq(alice.id()), any())).willReturn(11L);
            Long roomId = roomId(alice, bob.id());
            String clientMessageId = UUID.randomUUID().toString();

            send(alice, roomId, clientMessageId, "도배")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.error.code").value("DM_007"));

            assertThat(dmMessageRepository.findBySenderIdAndClientMessageId(alice.id(), clientMessageId)).isEmpty();
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

            ResultActions page1 = getMessages(alice, roomId, null, 2)
                    .andExpect(jsonPath("$.data.content[0].content").value("메시지 5"))
                    .andExpect(jsonPath("$.data.content[1].content").value("메시지 4"))
                    .andExpect(jsonPath("$.data.hasNext").value(true));
            Long cursor = longValue(page1, "$.data.nextCursor");

            ResultActions page2 = getMessages(bob, roomId, cursor, 2)
                    .andExpect(jsonPath("$.data.content[0].content").value("메시지 3"))
                    .andExpect(jsonPath("$.data.content[1].content").value("메시지 2"));
            cursor = longValue(page2, "$.data.nextCursor");

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

        private ResultActions getMessages(Member member, Long roomId, Long cursor, int size) throws Exception {
            var request = get("/api/v1/dm/rooms/" + roomId + "/messages")
                    .param("size", String.valueOf(size))
                    .header(HttpHeaders.AUTHORIZATION, member.bearer());
            if (cursor != null) {
                request.param("cursor", String.valueOf(cursor));
            }
            return mockMvc.perform(request);
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

            sendAndGetId(alice, withBob, "밥에게 첫 메시지");
            sendAndGetId(carol, withCarol, "캐롤이 보낸\n두 줄 메시지");
            sendAndGetId(bob, others, "남의 대화");

            getRooms(alice)
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
            sendAndGetId(alice, withBob, "먼저 보낸 방");
            sendAndGetId(alice, withCarol, "나중에 보낸 방");
            sendAndGetId(bob, withBob, "다시 최신");

            ResultActions page1 = mockMvc.perform(get("/api/v1/dm/rooms").param("size", "1")
                            .header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                    .andExpect(jsonPath("$.data.content[0].roomId").value(withBob))
                    .andExpect(jsonPath("$.data.content[0].lastMessagePreview").value("다시 최신"))
                    .andExpect(jsonPath("$.data.hasNext").value(true));
            String cursor = String.valueOf(longValue(page1, "$.data.nextCursor"));

            mockMvc.perform(get("/api/v1/dm/rooms").param("size", "1").param("cursor", cursor)
                            .header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                    .andExpect(jsonPath("$.data.content[0].roomId").value(withCarol))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }
    }

    @Nested
    @DisplayName("읽음 처리와 읽지 않은 메시지 수")
    class Read {

        @Test
        @DisplayName("상대가 보낸 메시지는 읽지 않은 수로 세고, 내가 보낸 메시지는 세지 않는다")
        void unreadCounts() throws Exception {
            Member carol = signupAndLogin("캐롤");
            Long withBob = roomId(alice, bob.id());
            Long withCarol = roomId(alice, carol.id());
            sendAndGetId(bob, withBob, "1");
            sendAndGetId(bob, withBob, "2");
            sendAndGetId(alice, withBob, "내 답장");
            sendAndGetId(bob, withBob, "3");
            sendAndGetId(carol, withCarol, "캐롤 메시지");

            getRooms(alice)
                    .andExpect(jsonPath("$.data.content[0].roomId").value(withCarol))
                    .andExpect(jsonPath("$.data.content[0].unreadCount").value(1))
                    .andExpect(jsonPath("$.data.content[1].roomId").value(withBob))
                    // 내가 답장하면 그때까지는 읽은 것으로 보므로, 답장 뒤에 온 "3"만 남는다.
                    .andExpect(jsonPath("$.data.content[1].unreadCount").value(1));
            // 밥도 마지막에 "3"을 보냈으므로 그 앞의 내 답장까지 읽은 것으로 본다.
            getRooms(bob).andExpect(jsonPath("$.data.content[0].unreadCount").value(0));
            unreadTotal(alice).andExpect(jsonPath("$.data.unreadCount").value(2));
        }

        @Test
        @DisplayName("방을 읽으면 읽지 않은 수가 0이 되고, 상대에게 READ 이벤트가 가며, 상대의 방 정보에 내 읽음 위치가 보인다")
        void markAllRead() throws Exception {
            Long roomId = roomId(alice, bob.id());
            sendAndGetId(bob, roomId, "1");
            Long last = sendAndGetId(bob, roomId, "2");
            clearInvocations(stompBroadcaster);

            read(alice, roomId, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.lastReadMessageId").value(last))
                    .andExpect(jsonPath("$.data.unreadCount").value(0));

            DmEvent toBob = captureEvent(bob.id());
            assertThat(toBob.type()).isEqualTo(DmEvent.Type.READ);
            assertThat(toBob.readerId()).isEqualTo(alice.id());
            assertThat(toBob.lastReadMessageId()).isEqualTo(last);
            getRooms(bob).andExpect(jsonPath("$.data.content[0].otherLastReadMessageId").value(last));
            unreadTotal(alice).andExpect(jsonPath("$.data.unreadCount").value(0));
        }

        @Test
        @DisplayName("보인 메시지까지만 읽을 수 있고, 이미 읽은 위치보다 앞으로는 되돌리지 않으며 그때는 이벤트도 보내지 않는다")
        void partialAndBackward() throws Exception {
            Long roomId = roomId(alice, bob.id());
            Long first = sendAndGetId(bob, roomId, "1");
            Long second = sendAndGetId(bob, roomId, "2");
            sendAndGetId(bob, roomId, "3");

            read(alice, roomId, second)
                    .andExpect(jsonPath("$.data.lastReadMessageId").value(second))
                    .andExpect(jsonPath("$.data.unreadCount").value(1));
            clearInvocations(stompBroadcaster);

            read(alice, roomId, first)
                    .andExpect(jsonPath("$.data.lastReadMessageId").value(second))
                    .andExpect(jsonPath("$.data.unreadCount").value(1));
            then(stompBroadcaster).should(never()).sendToUser(anyLong(), anyString(), any());
        }

        @Test
        @DisplayName("방에 없는 큰 ID로 읽으면 마지막 메시지까지로 자른다")
        void clampToLastMessage() throws Exception {
            Long roomId = roomId(alice, bob.id());
            Long last = sendAndGetId(bob, roomId, "1");

            read(alice, roomId, last + 1000)
                    .andExpect(jsonPath("$.data.lastReadMessageId").value(last));
            Long next = sendAndGetId(bob, roomId, "2");
            getRooms(alice).andExpect(jsonPath("$.data.content[0].unreadCount").value(1));
            assertThat(next).isGreaterThan(last);
        }

        @Test
        @DisplayName("메시지가 없는 방을 읽어도 오류 없이 0을 응답하고, 참여하지 않은 방은 DM_002")
        void emptyRoomAndNotMember() throws Exception {
            Member carol = signupAndLogin("캐롤");
            Long roomId = roomId(alice, bob.id());

            read(alice, roomId, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.unreadCount").value(0));
            read(carol, roomId, null)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("DM_002"));
        }

        private ResultActions read(Member member, Long roomId, Long lastReadMessageId) throws Exception {
            var request = post("/api/v1/dm/rooms/" + roomId + "/read")
                    .header(HttpHeaders.AUTHORIZATION, member.bearer());
            if (lastReadMessageId != null) {
                request.contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastReadMessageId\": " + lastReadMessageId + "}");
            }
            return mockMvc.perform(request);
        }

        private ResultActions unreadTotal(Member member) throws Exception {
            return mockMvc.perform(get("/api/v1/dm/unread-count").header(HttpHeaders.AUTHORIZATION, member.bearer()));
        }
    }

    // receiverId에게 /queue/dm으로 보낸 마지막 이벤트
    private DmEvent captureEvent(Long receiverId) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        then(stompBroadcaster).should(atLeastOnce()).sendToUser(eq(receiverId), eq("/queue/dm"), captor.capture());
        return (DmEvent) captor.getValue();
    }
}
