package com.example.ddangddangddang.domain.livestream.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 방송 채팅을 AI가 검토한 뒤 어떻게 처리할지. 판매자가 방송마다 정한다. (서버에서 AI 검토를 켠 경우에만 의미가 있다)
 * 전송 전 규칙 검사(연락처·외부 링크·계좌번호·금칙어)는 어느 모드든 항상 한다.
 */
@Getter
@RequiredArgsConstructor
public enum ChatModerationMode {

    // AI 검토를 하지 않는다. (비용도 들지 않음)
    OFF("AI 검토 끔"),
    // 문제가 있어 보이는 채팅을 판매자에게만 알린다. 가릴지는 판매자가 판단한다.
    ALERT("판매자에게 알림"),
    // 명백한 위반은 바로 가리고 판매자에게 알린다. 판매자는 잘못 가려진 메시지를 되돌릴 수 있다. 애매한 것은 ALERT처럼 알리기만 한다.
    AUTO_HIDE("명백한 위반 자동 가림");

    private final String label;
}
