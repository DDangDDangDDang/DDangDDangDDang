package com.example.ddangddangddang.domain.livestream.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 방송 화면 박스 목록 ↔ JSON 글자. 박스는 방송 하나에 몇 개뿐이고 늘 함께 읽고 바꾸므로 따로 테이블을 두지 않고 한 칸에 둔다.
 * 비어 있으면 null로 저장하고, null은 빈 목록으로 읽는다.
 */
@Converter
public class OverlayBoxesConverter implements AttributeConverter<List<OverlayBox>, String> {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<OverlayBox>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<OverlayBox> boxes) {
        if (boxes == null || boxes.isEmpty()) {
            return null;
        }
        return MAPPER.writeValueAsString(boxes);
    }

    @Override
    public List<OverlayBox> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return List.copyOf(MAPPER.readValue(json, TYPE));
    }
}
