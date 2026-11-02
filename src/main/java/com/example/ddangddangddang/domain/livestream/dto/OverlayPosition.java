package com.example.ddangddangddang.domain.livestream.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 방송 화면 위 위치. x·y는 0~100(%)으로, 0이면 왼쪽(위쪽) 끝, 100이면 오른쪽(아래쪽) 끝, 50이면 가운데다.
 * hidden이 true면 판매자가 그 UI를 숨긴 것이라 시청 화면에 그리지 않는다. (보내지 않으면 보임)
 */
public record OverlayPosition(
        @NotNull(message = "가로 위치를 정해 주세요.")
        @Min(value = 0, message = "위치는 0에서 100 사이로 정해 주세요.")
        @Max(value = 100, message = "위치는 0에서 100 사이로 정해 주세요.")
        Integer x,
        @NotNull(message = "세로 위치를 정해 주세요.")
        @Min(value = 0, message = "위치는 0에서 100 사이로 정해 주세요.")
        @Max(value = 100, message = "위치는 0에서 100 사이로 정해 주세요.")
        Integer y,
        Boolean hidden
) {

    public OverlayPosition(Integer x, Integer y) {
        this(x, y, false);
    }

    // isHidden으로 두면 JSON 속성 hidden과 겹치므로 게터가 아닌 이름으로 둔다.
    public boolean hiddenOrFalse() {
        return Boolean.TRUE.equals(hidden);
    }
}
