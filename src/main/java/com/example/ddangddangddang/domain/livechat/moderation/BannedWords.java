package com.example.ddangddangddang.domain.livechat.moderation;

import java.util.List;

/**
 * 설정하지 않아도 항상 막는 기본 금칙어. (환경변수 CHAT_MODERATION_BANNED_WORDS로 단어를 더할 수 있다)
 *
 * 정상 문장에 자주 섞이는 말은 넣지 않는다. 예: "보지"(보지 마세요), "미친"(미친 가격), "씹"(씹다).
 * 이런 말과 돌려 쓴 욕설은 AI 검토가 맡는다. 규칙 검사는 확실한 경우만 막는다.
 */
final class BannedWords {

    private BannedWords() {
    }

    static final List<String> DEFAULTS = List.of(
            "씨발", "씨바", "씨팔", "시발", "ㅅㅂ", "ㅆㅂ", "ㅄ", "병신", "븅신",
            "좆", "개새끼", "개새기", "개색기", "개색끼", "개쌔끼",
            "미친년", "미친놈", "썅년", "썅놈", "니미럴", "느금마", "니애미", "니에미", "엠창"
    );

    /**
     * 금칙어를 포함하지만 정상인 말. 금칙어를 찾기 전에 지운다. (예: "시발점"의 "시발")
     */
    static final List<String> ALLOWED = List.of("시발점", "시발역", "시발택시");
}
