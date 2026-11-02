package com.example.ddangddangddang.domain.livestream.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.example.ddangddangddang.domain.livestream.dto.LiveStreamResponse;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LiveStreamCustomThumbnailServiceTest {

    private static final Long LIVE_STREAM_ID = 10L;
    private static final Long SELLER_ID = 1L;
    private static final String NEW_KEY = "live-thumbnails/10/custom/0f8fad5b-d9cb-469f-a165-70867728950e.png";
    private static final String OLD_KEY = "live-thumbnails/10/custom/7c9e6679-7425-40de-944b-e07fc1f90ae7.jpg";
    private static final String AUTO_KEY = "live-thumbnails/10/7c9e6679-7425-40de-944b-e07fc1f90ae7.jpg";

    @InjectMocks
    private LiveStreamCustomThumbnailService customThumbnailService;

    @Mock
    private LiveStreamRepository liveStreamRepository;

    @Mock
    private FileStorage fileStorage;

    private LiveStream liveStream;

    @BeforeEach
    void setUp() {
        liveStream = LiveStream.create(SELLER_ID, "테스트 방송", "설명");
        ReflectionTestUtils.setField(liveStream, "id", LIVE_STREAM_ID);
    }

    private void stubFind() {
        given(liveStreamRepository.findById(LIVE_STREAM_ID)).willReturn(Optional.of(liveStream));
    }

    @Test
    @DisplayName("방송 시작 전에도 방송 주인은 이 방송의 custom 경로로 JPG·PNG·WEBP 업로드 URL을 받는다")
    void createUpload() {
        stubFind();
        given(fileStorage.presignUpload(anyString(), eq("image/webp"), eq(500_000L), any())).willReturn("https://upload");

        ThumbnailUploadResponse response = customThumbnailService.createUpload(LIVE_STREAM_ID, SELLER_ID, new ThumbnailUploadRequest("image/webp", 500_000L));

        assertThat(response.uploadUrl()).isEqualTo("https://upload");
        assertThat(response.thumbnailKey()).matches("live-thumbnails/10/custom/[0-9a-f-]{36}\\.webp");
    }

    @Test
    @DisplayName("GIF이거나 2MB보다 크면 업로드 URL을 주지 않고(LIVESTREAM_025), 주인이 아니거나 끝난 방송이면 막는다")
    void createUploadRejected() {
        stubFind();
        assertThatThrownBy(() -> customThumbnailService.createUpload(LIVE_STREAM_ID, SELLER_ID, new ThumbnailUploadRequest("image/gif", 30_000L)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.INVALID_CUSTOM_THUMBNAIL);
        assertThatThrownBy(() -> customThumbnailService.createUpload(LIVE_STREAM_ID, SELLER_ID, new ThumbnailUploadRequest("image/png", 2 * 1024 * 1024 + 1L)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.INVALID_CUSTOM_THUMBNAIL);
        assertThatThrownBy(() -> customThumbnailService.createUpload(LIVE_STREAM_ID, 2L, new ThumbnailUploadRequest("image/png", 30_000L)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.NOT_OWNER);

        ReflectionTestUtils.setField(liveStream, "status", LiveStreamStatus.ENDED);
        assertThatThrownBy(() -> customThumbnailService.createUpload(LIVE_STREAM_ID, SELLER_ID, new ThumbnailUploadRequest("image/png", 30_000L)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.ALREADY_ENDED);
        then(fileStorage).should(never()).presignUpload(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("올라간 이미지로 바꾸면 응답에 화면 주소를 주고, 앞에 올린 썸네일은 지운다. 자동 캡처는 그대로 둔다")
    void change() {
        stubFind();
        ReflectionTestUtils.setField(liveStream, "customThumbnailKey", OLD_KEY);
        ReflectionTestUtils.setField(liveStream, "thumbnailKey", AUTO_KEY);
        given(fileStorage.find(NEW_KEY)).willReturn(Optional.of(new FileStorage.StoredFile(500_000L, "image/png")));
        given(fileStorage.publicUrl(NEW_KEY)).willReturn("https://cdn/" + NEW_KEY);

        LiveStreamResponse response = customThumbnailService.change(LIVE_STREAM_ID, SELLER_ID, NEW_KEY);

        assertThat(response.customThumbnailUrl()).isEqualTo("https://cdn/" + NEW_KEY);
        assertThat(liveStream.getCustomThumbnailKey()).isEqualTo(NEW_KEY);
        assertThat(liveStream.getThumbnailKey()).isEqualTo(AUTO_KEY);
        then(liveStreamRepository).should().saveAndFlush(liveStream);
        then(fileStorage).should().delete(OLD_KEY);
        then(fileStorage).should(never()).delete(AUTO_KEY);
    }

    @Test
    @DisplayName("이미 쓰는 키로 다시 확정하면 저장소를 다시 확인하지 않고 지우지도 않는다")
    void changeSameKey() {
        stubFind();
        ReflectionTestUtils.setField(liveStream, "customThumbnailKey", NEW_KEY);
        given(fileStorage.publicUrl(NEW_KEY)).willReturn("https://cdn/" + NEW_KEY);

        customThumbnailService.change(LIVE_STREAM_ID, SELLER_ID, NEW_KEY);

        then(fileStorage).should(never()).find(anyString());
        then(fileStorage).should(never()).delete(anyString());
        then(liveStreamRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("다른 방송·자동 캡처 경로의 키나 올라가지 않은 파일은 막고(LIVESTREAM_023), 형식·크기가 다르면 막는다(LIVESTREAM_025)")
    void changeRejected() {
        stubFind();
        assertThatThrownBy(() -> customThumbnailService.change(LIVE_STREAM_ID, SELLER_ID, "live-thumbnails/11/custom/0f8fad5b-d9cb-469f-a165-70867728950e.png"))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.THUMBNAIL_NOT_UPLOADED);
        assertThatThrownBy(() -> customThumbnailService.change(LIVE_STREAM_ID, SELLER_ID, AUTO_KEY))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.THUMBNAIL_NOT_UPLOADED);

        given(fileStorage.find(NEW_KEY)).willReturn(Optional.empty());
        assertThatThrownBy(() -> customThumbnailService.change(LIVE_STREAM_ID, SELLER_ID, NEW_KEY))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.THUMBNAIL_NOT_UPLOADED);

        given(fileStorage.find(NEW_KEY)).willReturn(Optional.of(new FileStorage.StoredFile(500_000L, "image/gif")));
        assertThatThrownBy(() -> customThumbnailService.change(LIVE_STREAM_ID, SELLER_ID, NEW_KEY))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.INVALID_CUSTOM_THUMBNAIL);

        given(fileStorage.find(NEW_KEY)).willReturn(Optional.of(new FileStorage.StoredFile(3_000_000L, "image/png")));
        assertThatThrownBy(() -> customThumbnailService.change(LIVE_STREAM_ID, SELLER_ID, NEW_KEY))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.INVALID_CUSTOM_THUMBNAIL);
        assertThat(liveStream.getCustomThumbnailKey()).isNull();
    }

    @Test
    @DisplayName("끝난 방송이나 남의 방송은 바꾸지 못한다")
    void changeNotEditable() {
        stubFind();
        assertThatThrownBy(() -> customThumbnailService.change(LIVE_STREAM_ID, 2L, NEW_KEY))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.NOT_OWNER);
        ReflectionTestUtils.setField(liveStream, "status", LiveStreamStatus.ENDED);
        assertThatThrownBy(() -> customThumbnailService.delete(LIVE_STREAM_ID, SELLER_ID))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.ALREADY_ENDED);
        then(fileStorage).should(never()).delete(anyString());
    }

    @Test
    @DisplayName("방송 정보 수정과 겹쳐 버전이 맞지 않으면 LIVESTREAM_006으로 막고 앞의 썸네일을 지우지 않는다")
    void changeConflict() {
        stubFind();
        ReflectionTestUtils.setField(liveStream, "customThumbnailKey", OLD_KEY);
        given(fileStorage.find(NEW_KEY)).willReturn(Optional.of(new FileStorage.StoredFile(500_000L, "image/png")));
        willThrow(new ObjectOptimisticLockingFailureException(LiveStream.class, LIVE_STREAM_ID)).given(liveStreamRepository).saveAndFlush(liveStream);

        assertThatThrownBy(() -> customThumbnailService.change(LIVE_STREAM_ID, SELLER_ID, NEW_KEY))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.CONCURRENT_UPDATE);
        then(fileStorage).should(never()).delete(anyString());
    }

    @Test
    @DisplayName("지우면 직접 올린 파일을 지우고, 카드는 방송 중 자동 캡처로 돌아간다")
    void delete() {
        stubFind();
        ReflectionTestUtils.setField(liveStream, "status", LiveStreamStatus.LIVE);
        ReflectionTestUtils.setField(liveStream, "customThumbnailKey", OLD_KEY);
        ReflectionTestUtils.setField(liveStream, "thumbnailKey", AUTO_KEY);
        assertThat(liveStream.cardThumbnailKey()).isEqualTo(OLD_KEY);

        LiveStreamResponse response = customThumbnailService.delete(LIVE_STREAM_ID, SELLER_ID);

        assertThat(response.customThumbnailUrl()).isNull();
        assertThat(liveStream.cardThumbnailKey()).isEqualTo(AUTO_KEY);
        then(fileStorage).should().delete(OLD_KEY);
    }

    @Test
    @DisplayName("카드 썸네일: 직접 올린 것이 먼저이고, 없으면 방송 중일 때만 자동 캡처를 쓴다")
    void cardThumbnailKey() {
        ReflectionTestUtils.setField(liveStream, "thumbnailKey", AUTO_KEY);
        assertThat(liveStream.cardThumbnailKey()).isNull(); // 방송 예정: 자동 캡처를 보여 주지 않는다

        ReflectionTestUtils.setField(liveStream, "status", LiveStreamStatus.LIVE);
        assertThat(liveStream.cardThumbnailKey()).isEqualTo(AUTO_KEY);

        ReflectionTestUtils.setField(liveStream, "status", LiveStreamStatus.ENDED);
        assertThat(liveStream.cardThumbnailKey()).isNull();

        ReflectionTestUtils.setField(liveStream, "customThumbnailKey", NEW_KEY);
        assertThat(liveStream.cardThumbnailKey()).isEqualTo(NEW_KEY);
    }
}
