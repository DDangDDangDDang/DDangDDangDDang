package com.example.ddangddangddang.domain.livestream.dto;

import com.example.ddangddangddang.domain.livestream.entity.OverlayBoxType;
import com.example.ddangddangddang.domain.livestream.entity.OverlayTextSize;
import com.example.ddangddangddang.domain.livestream.entity.OverlayTextStyle;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 판매자가 방송 화면에 넣는 박스 하나.
 * - 텍스트 박스(TEXT): text(1~40자), textStyle, textSize를 보낸다.
 * - 이미지 박스(IMAGE): imageKey(이미지 업로드 URL로 받은 키)와 width(영상 폭의 10~60%)를 보낸다.
 * 종류에 맞지 않는 값은 무시한다. 종류에 필요한 값이 빠졌는지는 서비스에서 확인한다.
 */
public record OverlayBoxRequest(
        @NotNull(message = "박스 종류를 정해 주세요.")
        OverlayBoxType type,
        @NotNull(message = "가로 위치를 정해 주세요.")
        @Min(value = 0, message = "위치는 0에서 100 사이로 정해 주세요.")
        @Max(value = 100, message = "위치는 0에서 100 사이로 정해 주세요.")
        Integer x,
        @NotNull(message = "세로 위치를 정해 주세요.")
        @Min(value = 0, message = "위치는 0에서 100 사이로 정해 주세요.")
        @Max(value = 100, message = "위치는 0에서 100 사이로 정해 주세요.")
        Integer y,
        @Size(max = MAX_TEXT_LENGTH, message = "텍스트 박스에는 40자까지 쓸 수 있습니다.")
        String text,
        OverlayTextStyle textStyle,
        OverlayTextSize textSize,
        @Size(max = 200, message = "이미지를 다시 올려 주세요.")
        String imageKey,
        @Min(value = MIN_IMAGE_WIDTH, message = "이미지 크기는 영상 폭의 10%에서 60% 사이로 정해 주세요.")
        @Max(value = MAX_IMAGE_WIDTH, message = "이미지 크기는 영상 폭의 10%에서 60% 사이로 정해 주세요.")
        Integer width
) {
    public static final int MAX_TEXT_LENGTH = 40;
    public static final int MIN_IMAGE_WIDTH = 10;
    public static final int MAX_IMAGE_WIDTH = 60;
}
