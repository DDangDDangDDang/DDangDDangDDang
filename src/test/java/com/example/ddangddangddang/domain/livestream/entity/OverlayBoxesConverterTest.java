package com.example.ddangddangddang.domain.livestream.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OverlayBoxesConverterTest {

    private final OverlayBoxesConverter converter = new OverlayBoxesConverter();

    @Test
    @DisplayName("박스 목록을 JSON으로 저장했다가 같은 목록으로 읽는다")
    void roundTrip() {
        List<OverlayBox> boxes = List.of(
                OverlayBox.text(50, 10, "오늘 마감 특가", OverlayTextStyle.HIGHLIGHT, OverlayTextSize.LARGE),
                OverlayBox.image(100, 0, "overlay-images/1/a.png", "https://cdn/a.png", 30));

        String json = converter.convertToDatabaseColumn(boxes);

        assertThat(converter.convertToEntityAttribute(json)).isEqualTo(boxes);
    }

    @Test
    @DisplayName("빈 목록은 null로 저장하고, null은 빈 목록으로 읽는다")
    void empty() {
        assertThat(converter.convertToDatabaseColumn(List.of())).isNull();
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
    }
}
