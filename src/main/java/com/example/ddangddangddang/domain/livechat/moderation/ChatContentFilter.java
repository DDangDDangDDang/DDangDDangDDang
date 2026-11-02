package com.example.ddangddangddang.domain.livechat.moderation;

import com.example.ddangddangddang.domain.livechat.exception.LiveChatErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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

    // 한글로 쓴 숫자: "공일공 일이삼사 오육칠팔". 숫자로 바꾼 뒤 위의 휴대폰 번호 검사를 한 번 더 한다.
    // 10자리 이상이 이어지고 01X로 시작해야 걸리므로 "사이즈", "이일" 같은 보통 글자는 걸리지 않는다.
    private static final String KOREAN_DIGITS = "공영빵일이삼사오육륙칠팔구";
    private static final String KOREAN_DIGIT_VALUES = "0001234566789";

    // 외부 메신저·오픈채팅 링크
    private static final Pattern EXTERNAL_LINK = Pattern.compile(
            "(open\\.kakao\\.com|pf\\.kakao\\.com|t\\.me/|telegram\\.me/|line\\.me/)", Pattern.CASE_INSENSITIVE);

    // 외부 메신저 아이디를 주고받는 말: "카톡 아이디", "텔레 id", "오픈카톡", "갠톡"
    // "카톡 프로필 사진 예쁘네요"처럼 메신저 이름만 나오는 것은 막지 않는다.
    // "카카오 아이디로 가입했어요"(카카오 로그인), "온라인 아이디"는 정상 문장이라 걸리지 않게 한다.
    private static final Pattern MESSENGER_ID = Pattern.compile(
            "(카톡|카카오톡|katalk|텔레그램|텔레|telegram|(?<!온)라인|(?<!on)line|위챗|wechat|인스타|insta(?:gram)?)\\s*(아이디|아이뒤|아뒤|아디|id)"
                    + "|오픈\\s*카톡|오픈\\s*채팅|갠\\s*톡|갠\\s*카톡|개인\\s*톡",
            Pattern.CASE_INSENSITIVE);

    // 계좌번호 후보: 숫자 묶음 3개 이상을 하이픈으로 이은 것. 날짜(2026-10-14)와 구분하려고 숫자가 10자리 이상일 때만 본다.
    private static final Pattern DIGIT_GROUPS = Pattern.compile("\\d{2,6}(?:-\\d{2,8}){2,}");
    private static final int MIN_ACCOUNT_DIGITS = 10;

    // 금칙어를 찾기 전에 지우는 문자: 공백이 아닌 문장부호·기호 ("씨.발", "병*신")
    private static final Pattern PUNCTUATION = Pattern.compile("[\\p{P}\\p{S}]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final List<String> bannedWords;

    public ChatContentFilter(ChatModerationProperties properties) {
        this.bannedWords = Stream.concat(BannedWords.DEFAULTS.stream(), properties.bannedWords().stream())
                .map(ChatContentFilter::compact)
                .filter(word -> !word.isEmpty())
                .distinct()
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
        // 전각 문자(０１０, ｏｐｅｎ.ｋａｋａｏ)를 보통 문자로 바꿔 규칙을 피하지 못하게 한다.
        String normalized = Normalizer.normalize(content, Normalizer.Form.NFKC);
        if (containsPhoneNumber(normalized) || containsPhoneNumber(koreanDigitsToNumbers(normalized))) {
            return Optional.of(ContentViolation.PHONE_NUMBER);
        }
        if (EXTERNAL_LINK.matcher(normalized).find()) {
            return Optional.of(ContentViolation.EXTERNAL_LINK);
        }
        if (MESSENGER_ID.matcher(normalized).find()) {
            return Optional.of(ContentViolation.MESSENGER_ID);
        }
        if (containsAccountNumber(normalized)) {
            return Optional.of(ContentViolation.ACCOUNT_NUMBER);
        }
        if (containsBannedWord(normalized)) {
            return Optional.of(ContentViolation.BANNED_WORD);
        }
        return Optional.empty();
    }

    private boolean containsPhoneNumber(String content) {
        return PHONE_NUMBER.matcher(content).find() || containsSeparatedPhoneNumber(content);
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

    static String koreanDigitsToNumbers(String content) {
        StringBuilder sb = new StringBuilder(content.length());
        for (char c : content.toCharArray()) {
            int index = KOREAN_DIGITS.indexOf(c);
            sb.append(index < 0 ? c : KOREAN_DIGIT_VALUES.charAt(index));
        }
        return sb.toString();
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

    private boolean containsBannedWord(String content) {
        String text = joinSingleLetters(compactKeepingSpaces(content));
        for (String allowed : BannedWords.ALLOWED) {
            text = text.replace(allowed, " ");
        }
        String target = text;
        return bannedWords.stream().anyMatch(target::contains);
    }

    /**
     * 한 글자씩 띄어 쓴 단어("씨 발", "ㅅ ㅂ")는 붙인다.
     * 모든 공백을 지우면 "물병 신상" → "물병신상"처럼 다른 단어가 이어져 금칙어가 생기므로, 한 글자짜리끼리만 붙인다.
     */
    static String joinSingleLetters(String content) {
        String[] tokens = WHITESPACE.split(content.strip());
        List<String> merged = new ArrayList<>(tokens.length);
        StringBuilder run = new StringBuilder();
        for (String token : tokens) {
            if (token.codePointCount(0, token.length()) == 1) {
                run.append(token);
                continue;
            }
            if (!run.isEmpty()) {
                merged.add(run.toString());
                run.setLength(0);
            }
            merged.add(token);
        }
        if (!run.isEmpty()) {
            merged.add(run.toString());
        }
        return String.join(" ", merged);
    }

    // 소문자로 바꾸고 문장부호·기호를 지운다. 공백은 남긴다.
    private static String compactKeepingSpaces(String content) {
        return PUNCTUATION.matcher(Normalizer.normalize(content, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)).replaceAll("");
    }

    // 금칙어 자체는 공백까지 지운 형태로 비교한다.
    private static String compact(String word) {
        return WHITESPACE.matcher(compactKeepingSpaces(word.strip())).replaceAll("");
    }
}
