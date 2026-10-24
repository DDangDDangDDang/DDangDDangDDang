package com.example.ddangddangddang.domain.livechat.dto;

public enum ChatMessageType {
    CHAT,   // 사용자 채팅
    SYSTEM,   // 서버 안내 (방송 종료 등)
    DONATION  // 도네이션 포인트 선물 (방송 화면 이펙트: "누구님이 몇 P를 선물했어요")
}
