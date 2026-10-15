package com.example.ddangddangddang.domain.livestream.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 방송 정보 수정. 보낸 값만 바꾸고, 보내지 않은(null) 값은 그대로 둔다.
 * description을 빈 문자열("")로 보내면 설명을 지운다.
 */
public record LiveStreamUpdateRequest(
        // null은 통과하고, 값을 보냈다면 공백만으로 이뤄질 수 없다.
        @Pattern(regexp = "(?s).*\\S.*", message = "방송 제목은 비워 둘 수 없습니다.")
        @Size(max = 100, message = "방송 제목은 100자 이하입니다.")
        String title,

        @Size(max = 1000, message = "방송 설명은 1000자 이하입니다.")
        String description
) {
}
