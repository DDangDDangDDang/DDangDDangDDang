package com.example.ddangddangddang.global.websocket;

import com.example.ddangddangddang.domain.auth.exception.AuthErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.exception.CommonErrorCode;
import com.example.ddangddangddang.global.security.jwt.AccessTokenAuthenticator;
import com.example.ddangddangddang.global.security.jwt.AuthenticatedToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * STOMP 프레임 검사: 토큰 없이 연결한 손님은 방송 토픽만 구독할 수 있고, 보내기와 개인 큐는 막는다.
 * 토큰을 보낸 연결은 지금처럼 인증하고, 잘못된 토큰은 손님으로 바꾸지 않고 거부한다.
 */
@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    @Mock
    private AccessTokenAuthenticator accessTokenAuthenticator;

    @InjectMocks
    private StompAuthChannelInterceptor interceptor;

    /** 한 WebSocket 연결의 세션 속성. CONNECT 뒤의 프레임도 같은 속성을 본다. */
    private final Map<String, Object> session = new HashMap<>();

    @Test
    @DisplayName("토큰 없이 연결하면 손님 연결이 되고, 방송 채팅·방송 정보 변경을 구독할 수 있다")
    void guestCanSubscribeLiveTopics() {
        assertThatCode(() -> send(StompCommand.CONNECT, null, null)).doesNotThrowAnyException();

        assertThatCode(() -> send(StompCommand.SUBSCRIBE, "/topic/live-streams/7/chat", null)).doesNotThrowAnyException();
        assertThatCode(() -> send(StompCommand.SUBSCRIBE, "/topic/live-streams/7", null)).doesNotThrowAnyException();
        verify(accessTokenAuthenticator, never()).authenticateForSession(anyString());
    }

    @Test
    @DisplayName("손님은 개인 큐(오류·검토 알림·1:1 채팅)를 구독할 수 없고 채팅을 보낼 수 없다")
    void guestCannotUsePrivateQueuesOrSend() {
        send(StompCommand.CONNECT, null, null);

        for (String destination : List.of("/user/queue/errors", "/user/queue/moderation", "/user/queue/dm")) {
            assertThatThrownBy(() -> send(StompCommand.SUBSCRIBE, destination, null))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_DESTINATION));
        }
        assertThatThrownBy(() -> send(StompCommand.SEND, "/app/live-streams/7/chat", null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.TOKEN_NOT_FOUND));
    }

    @Test
    @DisplayName("토큰으로 연결한 회원은 개인 큐도 구독할 수 있다")
    void memberCanSubscribePrivateQueues() {
        Instant expiresAt = Instant.now().plusSeconds(600);
        given(accessTokenAuthenticator.authenticateForSession("good"))
                .willReturn(new AuthenticatedToken(new UsernamePasswordAuthenticationToken("member", null, List.of()), "jti", expiresAt));

        send(StompCommand.CONNECT, null, "good");

        assertThatCode(() -> send(StompCommand.SUBSCRIBE, "/user/queue/dm", "member")).doesNotThrowAnyException();
        verify(accessTokenAuthenticator).validateStillActive("jti", expiresAt);
    }

    @Test
    @DisplayName("잘못된 토큰으로 연결하면 손님으로 바꾸지 않고 거부한다")
    void invalidTokenIsRejected() {
        given(accessTokenAuthenticator.authenticateForSession("expired"))
                .willThrow(new BusinessException(AuthErrorCode.TOKEN_NOT_FOUND));

        assertThatThrownBy(() -> send(StompCommand.CONNECT, null, "expired")).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Authorization 헤더가 있는데 Bearer 토큰이 아니면 손님으로 바꾸지 않고 거부한다")
    void malformedAuthorizationIsRejected() {
        for (String authorization : List.of("Basic abc", "Bearer ", "")) {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            accessor.setSessionAttributes(session);
            accessor.addNativeHeader(HttpHeaders.AUTHORIZATION, authorization);
            accessor.setLeaveMutable(true);
            Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            assertThatThrownBy(() -> interceptor.preSend(message, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_TOKEN);
        }
        verify(accessTokenAuthenticator, never()).authenticateForSession(anyString());
    }

    /**
     * 프레임 하나를 검사한다. token은 CONNECT의 Authorization 헤더, user는 CONNECT 뒤 Spring이 붙여 주는 사용자(손님이면 null)
     */
    private Message<byte[]> send(StompCommand command, String destination, String tokenOrUser) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionAttributes(session);
        if (command == StompCommand.CONNECT && tokenOrUser != null) {
            accessor.addNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOrUser);
        }
        if (command != StompCommand.CONNECT && tokenOrUser != null) {
            accessor.setUser(new UsernamePasswordAuthenticationToken(tokenOrUser, null, List.of()));
        }
        if (destination != null) {
            accessor.setDestination(destination);
        }
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        interceptor.preSend(message, null);
        return message;
    }
}
