package com.example.ddangddangddang.domain.livestream.entity;

/**
 * 판매자가 방송 화면에 직접 넣은 박스 하나. 텍스트 박스 또는 이미지 박스다.
 * 위치(x·y)는 다른 방송 화면 UI처럼 0~100(%)이다. (OverlayLayout)
 *
 * @param type      TEXT(글자) 또는 IMAGE(이미지)
 * @param text      텍스트 박스의 글자 (이미지 박스는 null)
 * @param textStyle 텍스트 박스 모양 (이미지 박스는 null)
 * @param textSize  텍스트 박스 글자 크기 (이미지 박스는 null)
 * @param imageKey  이미지 박스의 저장소 키 (텍스트 박스는 null). overlay-images/{방송 ID}/ 아래에 있다.
 * @param imageUrl  이미지 박스를 화면에 보여 줄 주소. 저장할 때 저장소에서 받아 둔다. (응답을 만들 때 저장소를 다시 부르지 않게)
 * @param width     이미지 박스 폭. 영상 폭의 %다. (텍스트 박스는 null, 글자 길이만큼 넓어진다)
 */
public record OverlayBox(
        OverlayBoxType type,
        int x,
        int y,
        String text,
        OverlayTextStyle textStyle,
        OverlayTextSize textSize,
        String imageKey,
        String imageUrl,
        Integer width
) {

    public static OverlayBox text(int x, int y, String text, OverlayTextStyle style, OverlayTextSize size) {
        return new OverlayBox(OverlayBoxType.TEXT, x, y, text, style, size, null, null, null);
    }

    public static OverlayBox image(int x, int y, String imageKey, String imageUrl, int width) {
        return new OverlayBox(OverlayBoxType.IMAGE, x, y, null, null, null, imageKey, imageUrl, width);
    }
}
