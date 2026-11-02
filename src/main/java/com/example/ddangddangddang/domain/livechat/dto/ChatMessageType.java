package com.example.ddangddangddang.domain.livechat.dto;

public enum ChatMessageType {
    CHAT,   // 사용자 채팅
    SYSTEM,   // 서버 안내 (방송 종료 등)
    DONATION,  // 도네이션 포인트 선물 (방송 화면 이펙트: "누구님이 몇 P를 선물했어요")
    BID,  // 방송에 올라간 경매에 입찰 (방송 화면 이펙트: "누가 어떤 상품을 얼마에 입찰했습니다")
    AUCTION_SOLD,  // 방송에 올라간 경매가 낙찰됨 (방송 화면 이펙트: "어떤 상품이 누구에게 얼마에 낙찰되었습니다")
    MESSAGE_HIDDEN,  // AI 검토로 targetMessageId 메시지(채팅·선물 한마디)를 가림. 화면은 내용 대신 "가려진 메시지"를 보여 준다.
    MESSAGE_RESTORED  // 판매자가 가려진 targetMessageId 메시지를 되돌림. 화면은 원래 내용을 다시 보여 준다.
}
