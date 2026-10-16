package com.example.ddangddangddang.domain.livechat.moderation;

import com.example.ddangddangddang.domain.livechat.exception.LiveChatErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 채팅을 보내기 전에 규칙으로 바로 막는 검사. (AI 검토 전 1차 방어선)
 * 명확한 경우만 막는다. 애매한 표현은 막지 않고 전송한 뒤 AI 검토(ChatModerationQueue)에 맡긴다.
 * 라이브 커머스에서 가장 흔한 사기 경로인 "외부 거래 유도"(연락처·계좌·외부 메신저)를 중심으로 본다.
 */
@Component
public class ChatContentFilter {

    // 휴대폰 번호: 010-1234-5678, 010 1234 5678, 01012345678, 010.1234.5678
    private static final Pattern PHONE_NUMBER = Pattern.compile("01[016789][-.\\s]?\\d{3,4}[-.\\s]?\\d{4}");

    // 위 형식을 피해 쓴 번호: +82 10 1234 5678, 0 1 0 1 2 3 4 5 6 7 8, 010 12 34 56 78
    // 숫자 사이 구분자(-, ., 공백)를 지운 뒤 묶음 전체가 휴대폰 번호인지 본다.
    // 묶음 전체가 01X(또는 82 1X)로 시작해야 하므로 날짜(2026 10 14)나 가격 나열(10000 11000)은 걸리지 않는다.
    private static final Pattern DIGIT_SEQUENCE = Pattern.compile("\\+?\\d(?:[-.\\s]{1,3}\\d|\\d)*");
    private static final Pattern NORMALIZED_PHONE_NUMBER = Pattern.compile("(?:0|82)1[016789]\\d{7,8}");

    // 외부 메신저·오픈채팅 링크
    private static final Pattern EXTERNAL_LINK = Pattern.compile(
            "(open\\.kakao\\.com|pf\\.kakao\\.com|t\\.me/|telegram\\.me/|line\\.me/)", Pattern.CASE_INSENSITIVE);

    // 계좌번호 후보: 숫자 묶음 3개 이상을 하이픈으로 이은 것. 날짜(2026-10-14)와 구분하려고 숫자가 10자리 이상일 때만 본다.
    private static final Pattern DIGIT_GROUPS = Pattern.compile("\\d{2,6}(?:-\\d{2,8}){2,}");
    private static final int MIN_ACCOUNT_DIGITS = 10;

    private final List<String> bannedWords;

    public ChatContentFilter(ChatModerationProperties properties) {
        this.bannedWords = properties.bannedWords().stream()
                .map(String::strip)
                .filter(word -> !word.isEmpty())
                .map(word -> word.toLowerCase(Locale.ROOT))
                .toList();
    }

    /**
     * 막아야 하는 내용이면 이유가 담긴 LIVECHAT_003 예외를 던진다.
     */
    public void validate(String content) {
        findViolation(content).ifPresent(violation -> {
            throw new BusinessException(LiveChatErrorCode.CONTENT_NOT_ALLOWED, violation.getChatMessage());
        });
    }

    /**
     * 막아야 하는 내용인지만 확인한다. 채팅이 아닌 곳(방송 제목·설명 등)에서 각자의 오류 코드로 거부할 때 쓴다.
     */
    public Optional<ContentViolation> findViolation(String content) {
        if (content == null || content.isEmpty()) {
            return Optional.empty();
        }
        if (PHONE_NUMBER.matcher(content).find() || containsSeparatedPhoneNumber(content)) {
            return Optional.of(ContentViolation.PHONE_NUMBER);
        }
        if (EXTERNAL_LINK.matcher(content).find()) {
            return Optional.of(ContentViolation.EXTERNAL_LINK);
        }
        if (containsAccountNumber(content)) {
            return Optional.of(ContentViolation.ACCOUNT_NUMBER);
        }
        String lower = content.toLowerCase(Locale.ROOT);
        if (bannedWords.stream().anyMatch(lower::contains)) {
            return Optional.of(ContentViolation.BANNED_WORD);
        }
        return Optional.empty();
    }

    private boolean containsSeparatedPhoneNumber(String content) {
        Matcher matcher = DIGIT_SEQUENCE.matcher(content);
        while (matcher.find()) {
            String digits = matcher.group().replaceAll("\\D", "");
            if (NORMALIZED_PHONE_NUMBER.matcher(digits).matches()) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAccountNumber(String content) {
        Matcher matcher = DIGIT_GROUPS.matcher(content);
        while (matcher.find()) {
            long digits = matcher.group().chars().filter(Character::isDigit).count();
            if (digits >= MIN_ACCOUNT_DIGITS) {
                return true;
            }
        }
        return false;
    }
}
