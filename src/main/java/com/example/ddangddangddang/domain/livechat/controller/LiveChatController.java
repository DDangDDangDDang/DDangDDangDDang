package com.example.ddangddangddang.domain.livechat.controller;

import com.example.ddangddangddang.domain.livechat.dto.ChatMessageRequest;
import com.example.ddangddangddang.domain.livechat.service.LiveChatService;
import com.example.ddangddangddang.global.common.ErrorResponse;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.exception.CommonErrorCode;
import com.example.ddangddangddang.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.validation.FieldError;

import java.security.Principal;

/**
 * 방송 채팅.
 * 클라이언트는 /app/live-streams/{liveStreamId}/chat 으로 보내고,
 * /topic/live-streams/{liveStreamId}/chat 을 구독해서 받는다.
 * 처리 중 오류는 보낸 사람에게만 /user/queue/errors 로 전달한다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class LiveChatController {

    private static final String ERROR_DESTINATION = "/queue/errors";

    private final LiveChatService liveChatService;

    @MessageMapping("/live-streams/{liveStreamId}/chat")
    public void send(@DestinationVariable Long liveStreamId,
                     @Valid @Payload ChatMessageRequest request,
                     Principal principal) {
        liveChatService.send(liveStreamId, toAuthUser(principal), request.content());
    }

    @MessageExceptionHandler(BusinessException.class)
    @SendToUser(destinations = ERROR_DESTINATION, broadcast = false)
    public ErrorResponse handleBusinessException(BusinessException e) {
        return ErrorResponse.of(e.getErrorCode(), e.getMessage());
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser(destinations = ERROR_DESTINATION, broadcast = false)
    public ErrorResponse handleValidationException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult() != null ? e.getBindingResult().getFieldError() : null;
        String message = fieldError != null
                ? fieldError.getDefaultMessage()
                : CommonErrorCode.INVALID_INPUT.getMessage();
        return ErrorResponse.of(CommonErrorCode.INVALID_INPUT, message);
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = ERROR_DESTINATION, broadcast = false)
    public ErrorResponse handleException(Exception e) {
        log.error("채팅 메시지 처리 중 알 수 없는 오류", e);
        return ErrorResponse.of(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    // CONNECT 때 StompAuthChannelInterceptor가 저장한 인증 정보
    private AuthUser toAuthUser(Principal principal) {
        return (AuthUser) ((Authentication) principal).getPrincipal();
    }
}
