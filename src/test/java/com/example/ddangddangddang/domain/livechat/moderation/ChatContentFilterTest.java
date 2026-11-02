package com.example.ddangddangddang.domain.livechat.moderation;

import com.example.ddangddangddang.domain.livechat.exception.LiveChatErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatContentFilterTest {

    private final ChatContentFilter filter =
            new ChatContentFilter(new ChatModerationProperties(List.of(" 금칙어 ", "BadWord"), null));

    @ParameterizedTest
    @ValueSource(strings = {"연락주세요 010-1234-5678", "01012345678로 문자", "010 1234 5678", "010.123.4567 입니다"})
    @DisplayName("휴대폰 번호는 막는다")
    void phoneNumber(String content) {
        assertRejected(content, "전화번호");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "+82 10-1234-5678 로 연락", "+82 10 1234 5678", "+821012345678", "821012345678",
            "0 1 0 1 2 3 4 5 6 7 8", "010 12 34 56 78", "010 - 1234 - 5678", "016 123 4567"
    })
    @DisplayName("+82 형식이나 공백으로 쪼갠 휴대폰 번호도 막는다")
    void separatedPhoneNumber(String content) {
        assertRejected(content, "전화번호");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://open.kakao.com/o/abc 로 오세요", "t.me/seller 텔레그램", "OPEN.KAKAO.COM/o/x"})
    @DisplayName("오픈채팅·텔레그램 링크는 막는다")
    void externalLink(String content) {
        assertRejected(content, "외부 메신저");
    }

    @ParameterizedTest
    @ValueSource(strings = {"입금은 110-123-456789", "3333-01-2345678 카카오뱅크"})
    @DisplayName("계좌번호로 보이는 숫자 묶음은 막는다")
    void accountNumber(String content) {
        assertRejected(content, "계좌번호");
    }

    @Test
    @DisplayName("설정한 금칙어는 앞뒤 공백과 대소문자를 무시하고 막는다")
    void bannedWord() {
        assertRejected("이건 금칙어 들어감", "사용할 수 없는 단어");
        assertRejected("this is a badword", "사용할 수 없는 단어");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "공일공 일이삼사 오육칠팔로 문자 주세요", "공일공-일이삼사-오육칠팔", "010 일이삼사 5678", "영일영 1234 5678",
            "０１０-１２３４-５６７８"
    })
    @DisplayName("한글이나 전각 숫자로 쓴 휴대폰 번호도 막는다")
    void obfuscatedPhoneNumber(String content) {
        assertRejected(content, "전화번호");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "카톡 아이디 seller77", "카톡아디 알려주세요", "텔레 id 있어요", "KakaoTalk 말고 katalk id: abc", "라인 아이디 줘요",
            "오픈카톡 들어오세요", "오픈 채팅방 있어요", "갠톡 주세요", "개인 톡 가능?", "인스타 아이디 팔로우요"
    })
    @DisplayName("외부 메신저 아이디를 주고받는 말은 막는다")
    void messengerId(String content) {
        assertRejected(content, "외부 메신저 아이디");
    }

    @ParameterizedTest
    @ValueSource(strings = {"씨발 뭐야", "ㅅㅂ 비싸네", "씨.발", "병*신같네", "씨 발 진짜", "ㅅ ㅂ", "개새끼야"})
    @DisplayName("기본 금칙어는 설정하지 않아도 막고, 기호를 섞거나 한 글자씩 띄어 써도 막는다")
    void defaultBannedWords(String content) {
        assertRejected(content, "사용할 수 없는 단어");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "이번 경매가 시발점이에요", "물병 신상 있어요?", "보지 마세요 지난 방송", "미친 가격이네", "카카오 아이디로 가입했어요",
            "온라인 아이디 찾기가 안돼요", "online id 문의", "사이즈 이일 남았나요", "오늘 일정 이상 없죠?"
    })
    @DisplayName("금칙어·메신저 이름·한글 숫자가 들어 있어도 정상 문장은 막지 않는다")
    void allowedLookalikes(String content) {
        assertThatCode(() -> filter.validate(content)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "안녕하세요 사이즈 몇이에요?",
            "15000원에 입찰할게요",
            "방송 2026-10-14 21:00에 또 해요?",
            "1,000,000원 너무 비싸요",
            "모델명 AB-1234 맞나요?",
            "2026 10 14 방송 몇 시예요?",
            "2026. 10. 14. 21:00",
            "10000 11000 12000 중에 뭐가 나아요?",
            "15000 20000 30000",
            "사이즈 01 10 사이 있어요?",
            "카톡 프로필 사진 예쁘네요"
    })
    @DisplayName("일반 대화, 가격, 날짜, 모델명은 막지 않는다 (애매한 표현은 AI 검토에 맡김)")
    void allowed(String content) {
        assertThatCode(() -> filter.validate(content)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("findViolation은 예외 없이 막는 이유를 알려 주고, 문제없거나 비어 있으면 빈 값을 준다 (방송 제목·설명 검사용)")
    void findViolation() {
        assertThat(filter.findViolation("문의 010-1234-5678")).contains(ContentViolation.PHONE_NUMBER);
        assertThat(filter.findViolation("open.kakao.com/o/abc")).contains(ContentViolation.EXTERNAL_LINK);
        assertThat(filter.findViolation("110-123-456789")).contains(ContentViolation.ACCOUNT_NUMBER);
        assertThat(filter.findViolation("금칙어")).contains(ContentViolation.BANNED_WORD);
        assertThat(filter.findViolation("카톡 아이디 abc")).contains(ContentViolation.MESSENGER_ID);
        assertThat(filter.findViolation("오늘은 신발 경매")).isEmpty();
        assertThat(filter.findViolation(null)).isEmpty();
        assertThat(filter.findViolation("")).isEmpty();
    }

    private void assertRejected(String content, String reasonKeyword) {
        assertThatThrownBy(() -> filter.validate(content))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(reasonKeyword)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveChatErrorCode.CONTENT_NOT_ALLOWED);
    }
}
