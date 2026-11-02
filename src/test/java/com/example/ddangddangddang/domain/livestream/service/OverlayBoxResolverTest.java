package com.example.ddangddangddang.domain.livestream.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.example.ddangddangddang.domain.livechat.moderation.ChatContentFilter;
import com.example.ddangddangddang.domain.livechat.moderation.ContentViolation;
import com.example.ddangddangddang.domain.livestream.dto.OverlayBoxRequest;
import com.example.ddangddangddang.domain.livestream.dto.OverlayImageUploadResponse;
import com.example.ddangddangddang.domain.livestream.entity.OverlayBox;
import com.example.ddangddangddang.domain.livestream.entity.OverlayBoxType;
import com.example.ddangddangddang.domain.livestream.entity.OverlayTextSize;
import com.example.ddangddangddang.domain.livestream.entity.OverlayTextStyle;
import com.example.ddangddangddang.domain.livestream.exception.LiveStreamErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.storage.FileStorage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OverlayBoxResolverTest {

    private static final Long LIVE_STREAM_ID = 10L;
    private static final String NEW_KEY = "overlay-images/10/0f8fad5b-d9cb-469f-a165-70867728950e.png";

    @InjectMocks
    private OverlayBoxResolver resolver;

    @Mock
    private FileStorage fileStorage;

    // 기본은 막을 내용 없음(Optional.empty). 막는 경우만 따로 정한다.
    @Mock
    private ChatContentFilter contentFilter;

    private static OverlayBoxRequest text(String text) {
        return new OverlayBoxRequest(OverlayBoxType.TEXT, 50, 20, text, null, null, null, null);
    }

    private static OverlayBoxRequest image(String key, Integer width) {
        return new OverlayBoxRequest(OverlayBoxType.IMAGE, 100, 0, null, null, null, key, width);
    }

    @Test
    @DisplayName("텍스트 박스는 앞뒤 공백을 지우고, 모양·크기를 정하지 않으면 기본(바탕 없음·보통)으로 둔다")
    void textBox() {
        List<OverlayBox> boxes = resolver.resolve(LIVE_STREAM_ID, List.of(), List.of(text("  오늘 마감 특가  ")));

        assertThat(boxes).containsExactly(OverlayBox.text(50, 20, "오늘 마감 특가", OverlayTextStyle.PLAIN, OverlayTextSize.MEDIUM));
    }

    @Test
    @DisplayName("빈 텍스트 박스는 막고(LIVESTREAM_019), 연락처 같은 금지 내용도 막는다(LIVESTREAM_009)")
    void textBoxRejected() {
        assertThatThrownBy(() -> resolver.resolve(LIVE_STREAM_ID, List.of(), List.of(text("   "))))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.OVERLAY_TEXT_REQUIRED);

        given(contentFilter.findViolation("010-1234-5678")).willReturn(Optional.of(ContentViolation.PHONE_NUMBER));
        assertThatThrownBy(() -> resolver.resolve(LIVE_STREAM_ID, List.of(), List.of(text("010-1234-5678"))))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.CONTENT_NOT_ALLOWED);
    }

    @Test
    @DisplayName("새 이미지는 실제로 올라갔는지 확인하고 보여 줄 주소를 받아 둔다. 폭을 정하지 않으면 25%")
    void newImage() {
        given(fileStorage.find(NEW_KEY)).willReturn(Optional.of(new FileStorage.StoredFile(1_000, "image/png")));
        given(fileStorage.publicUrl(NEW_KEY)).willReturn("https://cdn/new.png");

        List<OverlayBox> boxes = resolver.resolve(LIVE_STREAM_ID, List.of(), List.of(image(NEW_KEY, null)));

        assertThat(boxes).containsExactly(OverlayBox.image(100, 0, NEW_KEY, "https://cdn/new.png", 25));
    }

    @Test
    @DisplayName("이미 저장된 이미지는 저장소를 다시 확인하지 않고 위치·폭만 바꾼다")
    void savedImage() {
        OverlayBox saved = OverlayBox.image(0, 0, NEW_KEY, "https://cdn/new.png", 20);

        List<OverlayBox> boxes = resolver.resolve(LIVE_STREAM_ID, List.of(saved), List.of(image(NEW_KEY, 40)));

        assertThat(boxes).containsExactly(OverlayBox.image(100, 0, NEW_KEY, "https://cdn/new.png", 40));
        then(fileStorage).should(never()).find(anyString());
    }

    @Test
    @DisplayName("다른 방송의 키, 올라가지 않은 키, 너무 큰 이미지는 막는다")
    void imageRejected() {
        assertThatThrownBy(() -> resolver.resolve(LIVE_STREAM_ID, List.of(), List.of(image("overlay-images/99/0f8fad5b-d9cb-469f-a165-70867728950e.png", 20))))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.OVERLAY_IMAGE_NOT_UPLOADED);
        assertThatThrownBy(() -> resolver.resolve(LIVE_STREAM_ID, List.of(), List.of(image(null, 20))))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.OVERLAY_IMAGE_NOT_UPLOADED);

        given(fileStorage.find(NEW_KEY)).willReturn(Optional.empty());
        assertThatThrownBy(() -> resolver.resolve(LIVE_STREAM_ID, List.of(), List.of(image(NEW_KEY, 20))))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.OVERLAY_IMAGE_NOT_UPLOADED);

        given(fileStorage.find(NEW_KEY)).willReturn(Optional.of(new FileStorage.StoredFile(OverlayBoxResolver.IMAGE_MAX_BYTES + 1, "image/png")));
        assertThatThrownBy(() -> resolver.resolve(LIVE_STREAM_ID, List.of(), List.of(image(NEW_KEY, 20))))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.OVERLAY_IMAGE_TOO_LARGE);
    }

    @Test
    @DisplayName("업로드 URL은 이 방송의 키로 만들고, 형식·크기가 맞지 않으면 만들지 않는다")
    void imageUpload() {
        given(fileStorage.presignUpload(anyString(), eq("image/webp"), eq(1_000L), any())).willReturn("https://upload");
        given(fileStorage.publicUrl(anyString())).willReturn("https://cdn/x.webp");

        OverlayImageUploadResponse response = resolver.createImageUpload(LIVE_STREAM_ID, "image/webp", 1_000);

        assertThat(response.imageKey()).startsWith("overlay-images/10/").endsWith(".webp");
        assertThat(response.uploadUrl()).isEqualTo("https://upload");
        assertThatThrownBy(() -> resolver.createImageUpload(LIVE_STREAM_ID, "image/svg+xml", 1_000))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.UNSUPPORTED_OVERLAY_IMAGE_TYPE);
        assertThatThrownBy(() -> resolver.createImageUpload(LIVE_STREAM_ID, "image/png", OverlayBoxResolver.IMAGE_MAX_BYTES + 1))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.OVERLAY_IMAGE_TOO_LARGE);
        then(fileStorage).should().presignUpload(anyString(), eq("image/webp"), anyLong(), any());
    }

    @Test
    @DisplayName("빠진 이미지만 지운다 (트랜잭션 밖이면 바로)")
    void deleteRemoved() {
        OverlayBox kept = OverlayBox.image(0, 0, "overlay-images/10/kept.png", "u1", 20);
        OverlayBox removed = OverlayBox.image(0, 0, "overlay-images/10/removed.png", "u2", 20);

        resolver.deleteRemovedImagesAfterCommit(List.of(kept, removed), List.of(kept));

        then(fileStorage).should().delete("overlay-images/10/removed.png");
        then(fileStorage).should(never()).delete("overlay-images/10/kept.png");
    }
}
