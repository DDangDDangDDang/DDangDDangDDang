package com.example.ddangddangddang.global.websocket.broadcast;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = StompBroadcaster.PROPERTY, havingValue = "local")
public class LocalStompBroadcaster implements StompBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void broadcast(String destination, Object payload) {
        messagingTemplate.convertAndSend(destination, payload);
    }

    @Override
    public void sendToUser(Long userId, String destination, Object payload) {
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), destination, payload);
    }
}
