package com.example.ddangddangddang.global.websocket;

import com.example.ddangddangddang.domain.auth.exception.AuthErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.exception.CommonErrorCode;
import com.example.ddangddangddang.global.security.jwt.AccessTokenAuthenticator;
import com.example.ddangddangddang.global.security.jwt.AuthenticatedToken;
import com.example.ddangddangddang.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 클라이언트가 보낸 STOMP 프레임을 검사한다.
 * - CONNECT: Access Token으로 인증하고 사용자 정보를 세션에 저장한다. 이후 프레임은 이 사용자로 처리된다.
 * - SUBSCRIBE / SEND: 인증된 연결인지, 연결에 쓴 토큰이 아직 유효한지, 허용된 경로인지 확인한다.
 *   연결은 토큰 유효시간보다 오래 유지되므로, 연결 뒤에 토큰이 만료되거나 로그아웃되면 여기서 막고 연결을 끊는다.
 *
 * 여기서 던진 예외는 StompErrorHandler가 ERROR 프레임으로 바꿔 보내고 연결을 끊는다.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String APP_DESTINATION_PREFIX = "/app/";

    // CONNECT에 쓴 토큰 정보를 WebSocket 세션 속성에 저장하는 키
    private static final String TOKEN_ID_ATTRIBUTE = "accessTokenId";
    private static final String TOKEN_EXPIRES_AT_ATTRIBUTE = "accessTokenExpiresAt";

    // 클라이언트가 구독할 수 있는 경로. 새 토픽을 만들면 여기에 추가한다.
    private static final List<Pattern> SUBSCRIBABLE_DESTINATIONS = List.of(
            Pattern.compile("^/topic/live-streams/\\d+/chat$"),
            // 방송 정보 변경 알림 (제목·설명 수정)
            Pattern.compile("^/topic/live-streams/\\d+$"),
            Pattern.compile("^/user/queue/errors$"),
            // 판매자에게만 오는 채팅 검토 알림 (내 방송의 알림만 나에게 전달됨)
            Pattern.compile("^/user/queue/moderation$"),
            // 1:1 채팅 메시지 (나에게 온 메시지와 내가 다른 화면에서 보낸 메시지)
            Pattern.compile("^/user/queue/dm$")
    );

    private final AccessTokenAuthenticator accessTokenAuthenticator;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            authenticate(accessor);
        } else if (command == StompCommand.SUBSCRIBE) {
            validateAuthenticated(accessor);
            validateSubscribable(accessor.getDestination());
        } else if (command == StompCommand.SEND) {
            validateAuthenticated(accessor);
            validateSendable(accessor.getDestination());
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String token = JwtProvider.resolveBearerToken(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION));
        // 인증에 실패하면 Spring이 CONNECT 프레임 전체를 에러 로그로 남기므로, 토큰이 로그에 찍히지 않게 헤더에서 지운다.
        accessor.removeNativeHeader(HttpHeaders.AUTHORIZATION);
        if (token == null) {
            throw new BusinessException(AuthErrorCode.TOKEN_NOT_FOUND);
        }
        AuthenticatedToken authenticatedToken = accessTokenAuthenticator.authenticateForSession(token);
        accessor.setUser(authenticatedToken.authentication());

        Map<String, Object> sessionAttributes = sessionAttributes(accessor);
        sessionAttributes.put(TOKEN_ID_ATTRIBUTE, authenticatedToken.tokenId());
        sessionAttributes.put(TOKEN_EXPIRES_AT_ATTRIBUTE, authenticatedToken.expiresAt());
    }

    private void validateAuthenticated(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (accessor.getUser() == null || sessionAttributes == null
                || !(sessionAttributes.get(TOKEN_ID_ATTRIBUTE) instanceof String tokenId)
                || !(sessionAttributes.get(TOKEN_EXPIRES_AT_ATTRIBUTE) instanceof Instant expiresAt)) {
            throw new BusinessException(AuthErrorCode.TOKEN_NOT_FOUND);
        }
        accessTokenAuthenticator.validateStillActive(tokenId, expiresAt);
    }

    private Map<String, Object> sessionAttributes(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            throw new IllegalStateException("WebSocket 세션 속성이 없습니다.");
        }
        return sessionAttributes;
    }

    private void validateSubscribable(String destination) {
        if (destination == null
                || SUBSCRIBABLE_DESTINATIONS.stream().noneMatch(pattern -> pattern.matcher(destination).matches())) {
            throw new BusinessException(CommonErrorCode.INVALID_DESTINATION);
        }
    }

    /**
     * 클라이언트는 /app 경로로만 보낼 수 있다.
     * /topic으로 직접 보내면 서버 검증 없이 브로커가 그대로 전체 전송하므로, 남을 사칭한 메시지를 막기 위해 차단한다.
     */
    private void validateSendable(String destination) {
        if (destination == null || !destination.startsWith(APP_DESTINATION_PREFIX)) {
            throw new BusinessException(CommonErrorCode.INVALID_DESTINATION);
        }
    }
}
