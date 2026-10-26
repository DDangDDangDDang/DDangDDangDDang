package com.example.ddangddangddang.global.util;

/**
 * 누구나 보는 화면(경매 입찰 기록, 실시간 입찰 알림)에 닉네임을 가려서 보여준다.
 * 첫 글자와 마지막 글자만 남기고 가운데를 *로 바꾼다. 두 글자면 뒤 글자를, 한 글자면 전부 가린다.
 * 예) "땅땅이" → "땅*이", "경매왕김씨" → "경***씨", "철수" → "철*", "a" → "*"
 */
public final class NicknameMasker {

    public static final String UNKNOWN = "알 수 없음";

    private NicknameMasker() {
    }

    public static String mask(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return UNKNOWN;
        }
        int[] chars = nickname.strip().codePoints().toArray(); // 이모지처럼 두 칸짜리 글자도 한 글자로 센다
        int length = chars.length;
        if (length == 1) {
            return "*";
        }
        if (length == 2) {
            return new String(chars, 0, 1) + "*";
        }
        return new String(chars, 0, 1) + "*".repeat(length - 2) + new String(chars, length - 1, 1);
    }
}
