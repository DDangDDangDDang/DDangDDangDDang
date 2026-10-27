package com.example.ddangddangddang.global.websocket.broadcast;

import tools.jackson.databind.JsonNode;

/**
 * Redis 채널로 서버끼리 주고받는 메시지.
 * payload를 JSON 그대로 담아서, 받는 서버가 원래 타입을 몰라도 구독자에게 그대로 전달할 수 있다.
 * user가 있으면 그 사용자에게만 보내는 메시지다. (없으면 destination 구독자 전체)
 */
public record StompBroadcastMessage(String destination, JsonNode payload, String user) {

    public static StompBroadcastMessage toTopic(String destination, JsonNode payload) {
        return new StompBroadcastMessage(destination, payload, null);
    }

    public static StompBroadcastMessage toUser(String user, String destination, JsonNode payload) {
        return new StompBroadcastMessage(destination, payload, user);
    }
}
