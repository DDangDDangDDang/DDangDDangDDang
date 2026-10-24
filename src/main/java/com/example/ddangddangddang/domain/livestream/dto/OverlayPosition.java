package com.example.ddangddangddang.domain.livestream.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 방송 화면 위 위치. x·y는 0~100(%)으로, 0이면 왼쪽(위쪽) 끝, 100이면 오른쪽(아래쪽) 끝, 50이면 가운데다.
 */
public record OverlayPosition(
        @NotNull(message = "가로 위치를 정해 주세요.")
        @Min(value = 0, message = "위치는 0에서 100 사이로 정해 주세요.")
        @Max(value = 100, message = "위치는 0에서 100 사이로 정해 주세요.")
        Integer x,
        @NotNull(message = "세로 위치를 정해 주세요.")
        @Min(value = 0, message = "위치는 0에서 100 사이로 정해 주세요.")
        @Max(value = 100, message = "위치는 0에서 100 사이로 정해 주세요.")
        Integer y
) {
}
