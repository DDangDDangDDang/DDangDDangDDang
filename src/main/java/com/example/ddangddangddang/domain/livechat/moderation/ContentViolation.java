package com.example.ddangddangddang.domain.livechat.moderation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * ChatContentFilter가 막는 내용의 종류.
 * chatMessage는 채팅을 거부할 때, message는 방송 제목·설명처럼 채팅이 아닌 곳에서 거부할 때 보여 준다.
 */
@Getter
@RequiredArgsConstructor
public enum ContentViolation {

    PHONE_NUMBER("전화번호는 채팅으로 보낼 수 없습니다.", "전화번호는 넣을 수 없습니다."),
    EXTERNAL_LINK("외부 메신저나 오픈채팅 링크는 보낼 수 없습니다.", "외부 메신저나 오픈채팅 링크는 넣을 수 없습니다."),
    ACCOUNT_NUMBER("계좌번호로 보이는 내용은 보낼 수 없습니다.", "계좌번호로 보이는 내용은 넣을 수 없습니다."),
    BANNED_WORD("사용할 수 없는 단어가 포함되어 있습니다.", "사용할 수 없는 단어가 포함되어 있습니다.");

    private final String chatMessage;
    private final String message;
}
