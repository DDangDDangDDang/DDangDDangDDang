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

import com.example.ddangddangddang.domain.livestream.dto.ThumbnailUploadRequest;
import com.example.ddangddangddang.domain.livestream.dto.ThumbnailUploadResponse;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.livestream.exception.LiveStreamErrorCode;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.storage.FileStorage;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LiveStreamThumbnailServiceTest {

    private static final Long LIVE_STREAM_ID = 10L;
    private static final Long SELLER_ID = 1L;
    private static final String NEW_KEY = "live-thumbnails/10/0f8fad5b-d9cb-469f-a165-70867728950e.jpg";
    private static final String OLD_KEY = "live-thumbnails/10/7c9e6679-7425-40de-944b-e07fc1f90ae7.jpg";

    @InjectMocks
    private LiveStreamThumbnailService thumbnailService;

    @Mock
    private LiveStreamRepository liveStreamRepository;

    @Mock
    private FileStorage fileStorage;

    private LiveStream liveStream;

    @BeforeEach
    void setUp() {
        liveStream = LiveStream.create(SELLER_ID, "테스트 방송", "설명");
        ReflectionTestUtils.setField(liveStream, "id", LIVE_STREAM_ID);
        ReflectionTestUtils.setField(liveStream, "status", LiveStreamStatus.LIVE);
        given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream));
    }

    @Test
    @DisplayName("방송 중인 방송 주인은 이 방송 경로의 JPG 업로드 URL을 받는다")
    void createUpload() {
        given(fileStorage.presignUpload(anyString(), eq("image/jpeg"), eq(30_000L), any())).willReturn("https://upload");

        ThumbnailUploadResponse response = thumbnailService.createUpload(LIVE_STREAM_ID, SELLER_ID, new ThumbnailUploadRequest("image/jpeg", 30_000L));

        assertThat(response.uploadUrl()).isEqualTo("https://upload");
        assertThat(response.thumbnailKey()).matches("live-thumbnails/10/[0-9a-f-]{36}\\.jpg");
    }

    @Test
    @DisplayName("JPG가 아니거나 1MB보다 크면 업로드 URL을 주지 않고(LIVESTREAM_024), 방송 중이 아니거나 주인이 아니면 막는다")
    void createUploadRejected() {
        assertThatThrownBy(() -> thumbnailService.createUpload(LIVE_STREAM_ID, SELLER_ID, new ThumbnailUploadRequest("image/png", 30_000L)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.INVALID_THUMBNAIL);
        assertThatThrownBy(() -> thumbnailService.createUpload(LIVE_STREAM_ID, SELLER_ID, new ThumbnailUploadRequest("image/jpeg", 2_000_000L)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.INVALID_THUMBNAIL);
        assertThatThrownBy(() -> thumbnailService.createUpload(LIVE_STREAM_ID, 2L, new ThumbnailUploadRequest("image/jpeg", 30_000L)))
                .isInstanceOf(BusinessException.class);

        ReflectionTestUtils.setField(liveStream, "status", LiveStreamStatus.SCHEDULED);
        assertThatThrownBy(() -> thumbnailService.createUpload(LIVE_STREAM_ID, SELLER_ID, new ThumbnailUploadRequest("image/jpeg", 30_000L)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.NOT_LIVE);
        then(fileStorage).should(never()).presignUpload(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("올라간 썸네일로 바꾸고 앞의 썸네일은 지운다")
    void change() {
        ReflectionTestUtils.setField(liveStream, "thumbnailKey", OLD_KEY);
        given(fileStorage.find(NEW_KEY)).willReturn(Optional.of(new FileStorage.StoredFile(30_000L, "image/jpeg")));
        given(liveStreamRepository.updateThumbnailKey(LIVE_STREAM_ID, NEW_KEY, OLD_KEY)).willReturn(1);

        thumbnailService.change(LIVE_STREAM_ID, SELLER_ID, NEW_KEY);

        then(fileStorage).should().delete(OLD_KEY);
    }

    @Test
    @DisplayName("읽은 뒤 다른 요청이 먼저 썸네일을 바꿨으면 바꾸지 않고(LIVESTREAM_006), 이번에 올린 파일만 지운다")
    void changeConflict() {
        ReflectionTestUtils.setField(liveStream, "thumbnailKey", OLD_KEY);
        given(fileStorage.find(NEW_KEY)).willReturn(Optional.of(new FileStorage.StoredFile(30_000L, "image/jpeg")));
        given(liveStreamRepository.updateThumbnailKey(LIVE_STREAM_ID, NEW_KEY, OLD_KEY)).willReturn(0);

        assertThatThrownBy(() -> thumbnailService.change(LIVE_STREAM_ID, SELLER_ID, NEW_KEY))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.CONCURRENT_UPDATE);
        then(fileStorage).should().delete(NEW_KEY);
        then(fileStorage).should(never()).delete(OLD_KEY);
    }

    @Test
    @DisplayName("다른 방송 경로의 키나 올라가지 않은 파일은 막고(LIVESTREAM_023), 형식이 다르면 올린 파일을 지우고 막는다(LIVESTREAM_024)")
    void changeRejected() {
        assertThatThrownBy(() -> thumbnailService.change(LIVE_STREAM_ID, SELLER_ID, "live-thumbnails/11/0f8fad5b-d9cb-469f-a165-70867728950e.jpg"))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.THUMBNAIL_NOT_UPLOADED);

        given(fileStorage.find(NEW_KEY)).willReturn(Optional.empty());
        assertThatThrownBy(() -> thumbnailService.change(LIVE_STREAM_ID, SELLER_ID, NEW_KEY))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.THUMBNAIL_NOT_UPLOADED);

        given(fileStorage.find(NEW_KEY)).willReturn(Optional.of(new FileStorage.StoredFile(30_000L, "image/png")));
        assertThatThrownBy(() -> thumbnailService.change(LIVE_STREAM_ID, SELLER_ID, NEW_KEY))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.INVALID_THUMBNAIL);
        then(fileStorage).should().delete(NEW_KEY);
        then(liveStreamRepository).should(never()).updateThumbnailKey(anyLong(), anyString(), any());
    }
}
