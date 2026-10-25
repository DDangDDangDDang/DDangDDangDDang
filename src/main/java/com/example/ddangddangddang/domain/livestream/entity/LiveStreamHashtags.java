package com.example.ddangddangddang.domain.livestream.entity;

import com.example.ddangddangddang.domain.livestream.exception.LiveStreamErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 방송 해시태그 규칙. 방송 목록 카드에 보여 주고, 방송 검색어로도 찾는다.
 * DB에는 "#나이키 #운동화"처럼 한 칸에 공백으로 이어 저장한다. (목록 검색을 제목·설명과 같은 LIKE로 하고, 카드마다 따로 조회하지 않도록)
 */
public final class LiveStreamHashtags {

    public static final int MAX_COUNT = 5;
    public static final int MAX_LENGTH = 20;
    // 저장 길이: 5개 × (# + 20자) + 공백 4개 = 109
    public static final int COLUMN_LENGTH = 120;

    // 한글(완성형)·영문·숫자·밑줄만. 다른 문자권(한자·가나 등)과 공백·#·특수문자는 넣을 수 없다. (공백·#은 태그 구분과 검색에 쓰임)
    private static final Pattern TAG = Pattern.compile("^[가-힣A-Za-z0-9_]{1," + MAX_LENGTH + "}$");

    private LiveStreamHashtags() {
    }

    /**
     * 입력을 저장할 태그 목록으로 바꾼다. 앞의 #과 앞뒤 공백은 빼고, 빈 값은 버리고, 대소문자만 다른 중복은 처음 것만 남긴다.
     * null이면 빈 목록.
     */
    public static List<String> normalize(List<String> input) {
        if (input == null) {
            return List.of();
        }
        Map<String, String> tags = new LinkedHashMap<>();
        for (String raw : input) {
            String tag = raw == null ? "" : raw.strip().replaceFirst("^#+", "").strip();
            if (tag.isEmpty()) {
                continue;
            }
            if (!TAG.matcher(tag).matches()) {
                throw new BusinessException(LiveStreamErrorCode.INVALID_HASHTAG);
            }
            tags.putIfAbsent(tag.toLowerCase(Locale.ROOT), tag);
        }
        if (tags.size() > MAX_COUNT) {
            throw new BusinessException(LiveStreamErrorCode.INVALID_HASHTAG, "해시태그는 " + MAX_COUNT + "개까지 넣을 수 있습니다.");
        }
        return new ArrayList<>(tags.values());
    }

    /** 저장 값. 태그가 없으면 null */
    static String toColumn(List<String> tags) {
        return tags.isEmpty() ? null : String.join(" ", tags.stream().map(tag -> "#" + tag).toList());
    }

    /** 저장 값을 태그 목록(# 없이)으로 */
    public static List<String> fromColumn(String column) {
        if (column == null || column.isBlank()) {
            return List.of();
        }
        return Arrays.stream(column.strip().split(" +")).map(tag -> tag.replaceFirst("^#", "")).toList();
    }
}
