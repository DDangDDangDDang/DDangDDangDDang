package com.example.ddangddangddang.domain.livestream.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 방송 정보 수정. 보낸 값만 바꾸고, 보내지 않은(null) 값은 그대로 둔다.
 * description, notice를 빈 문자열("")로 보내면 지운다. hashtags는 빈 목록([])으로 보내면 지운다.
 */
public record LiveStreamUpdateRequest(
        // null은 통과하고, 값을 보냈다면 공백만으로 이뤄질 수 없다. (?U)로 전각 공백 같은 유니코드 공백도 공백으로 본다.
        // 정규식이 못 거르는 문자도 있어, 최종 판단은 서비스에서 strip 뒤에 한다. (LiveStreamService.requireTitle)
        @Pattern(regexp = "(?sU).*\\S.*", message = "방송 제목은 비워 둘 수 없습니다.")
        @Size(max = 100, message = "방송 제목은 100자 이하입니다.")
        String title,

        @Size(max = 1000, message = "방송 설명은 1000자 이하입니다.")
        String description,

        // 방송 시작 예정 시간. 방송을 시작하기 전(대기 중)에만 바꿀 수 있고, 지금부터 30일 이내
        LocalDateTime scheduledStartAt,

        // 채팅창 위에 고정하는 공지. 시청자가 닫아도 다시 들어오면 보인다.
        @Size(max = 200, message = "공지는 200자 이하입니다.")
        String notice,

        // 해시태그 전체. 보내면 이 목록으로 바꾸고, 빈 목록([])이면 모두 지운다. (규칙은 LiveStreamCreateRequest.hashtags)
        List<String> hashtags
) {
    public LiveStreamUpdateRequest(String title, String description) {
        this(title, description, null, null);
    }

    public LiveStreamUpdateRequest(String title, String description, LocalDateTime scheduledStartAt) {
        this(title, description, scheduledStartAt, null);
    }

    public LiveStreamUpdateRequest(String title, String description, LocalDateTime scheduledStartAt, String notice) {
        this(title, description, scheduledStartAt, notice, null);
    }
}
