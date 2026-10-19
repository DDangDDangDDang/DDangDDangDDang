package com.example.ddangddangddang.global.websocket;

import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.exception.CommonErrorCode;
import com.example.ddangddangddang.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;
import tools.jackson.databind.ObjectMapper;

/**
 * 연결 단계(인터셉터)에서 난 예외를 ERROR 프레임으로 바꿔 보낸다. ERROR 프레임을 받으면 연결이 끊긴다.
 * message 헤더에는 에러 코드(AUTH_002 등)를, 본문에는 HTTP API와 같은 형식의 JSON을 담는다.
 * 프론트는 AUTH_002(만료)를 받으면 토큰을 재발급받고 다시 연결하면 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompErrorHandler extends StompSubProtocolErrorHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
        ErrorCode errorCode = findBusinessException(ex);
        if (errorCode == null) {
            log.error("STOMP 메시지 처리 중 알 수 없는 오류", ex);
            errorCode = CommonErrorCode.INTERNAL_SERVER_ERROR;
        }

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(errorCode.getCode());
        accessor.setContentType(MediaType.APPLICATION_JSON);
        accessor.setLeaveMutable(true);

        StompHeaderAccessor clientAccessor = clientMessage != null
                ? MessageHeaderAccessor.getAccessor(clientMessage, StompHeaderAccessor.class)
                : null;
        if (clientAccessor != null && clientAccessor.getReceipt() != null) {
            accessor.setReceiptId(clientAccessor.getReceipt());
        }

        byte[] payload = objectMapper.writeValueAsBytes(ApiResponse.fail(errorCode));
        return MessageBuilder.createMessage(payload, accessor.getMessageHeaders());
    }

    // 인터셉터에서 던진 예외는 MessageDeliveryException으로 감싸져 오므로 원인을 따라가며 찾는다.
    private ErrorCode findBusinessException(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof BusinessException businessException) {
                return businessException.getErrorCode();
            }
        }
        return null;
    }
}
