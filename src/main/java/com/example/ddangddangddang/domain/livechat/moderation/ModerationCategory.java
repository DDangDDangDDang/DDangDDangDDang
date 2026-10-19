package com.example.ddangddangddang.domain.livechat.moderation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI가 판매자 확인이 필요하다고 판단한 이유. 판매자 화면에는 label을 보여준다.
 */
@Getter
@RequiredArgsConstructor
public enum ModerationCategory {

    EXTERNAL_TRADE("외부 거래 유도"),
    SCAM("사기·사칭"),
    HARASSMENT("욕설·괴롭힘"),
    HATE("혐오 표현"),
    SEXUAL("성적인 내용"),
    SPAM("광고·스팸"),
    PERSONAL_INFO("개인정보 노출");

    private final String label;
}
