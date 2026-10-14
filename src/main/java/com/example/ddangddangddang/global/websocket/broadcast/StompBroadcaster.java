package com.example.ddangddangddang.global.websocket.broadcast;

/**
 * 구독 경로(/topic/...)의 구독자 전체에게 메시지를 보낸다.
 * 구독자가 어느 서버에 연결돼 있든 받도록, 서버 간 전달은 구현체가 맡는다.
 *
 * websocket.broadcast 설정으로 구현체를 고른다.
 * - redis (기본값): Redis Pub/Sub으로 모든 서버에 전달. 서버가 여러 대일 때 사용
 * - local: 이 서버의 구독자에게만 전달. Redis 없는 테스트용
 */
public interface StompBroadcaster {

    String PROPERTY = "websocket.broadcast";

    void broadcast(String destination, Object payload);
}
