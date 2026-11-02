package com.example.ddangddangddang.domain.ad.service;

import com.example.ddangddangddang.domain.ad.dto.AdCreateRequest;
import com.example.ddangddangddang.domain.ad.dto.AdImageUploadRequest;
import com.example.ddangddangddang.domain.ad.dto.AdImageUploadResponse;
import com.example.ddangddangddang.domain.ad.dto.AdResponse;
import com.example.ddangddangddang.domain.ad.entity.Ad;
import com.example.ddangddangddang.domain.ad.entity.AdPlacement;
import com.example.ddangddangddang.domain.ad.exception.AdErrorCode;
import com.example.ddangddangddang.domain.ad.repository.AdRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.storage.FileStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdServiceTest {

    private static final String KEY = "ads/123e4567-e89b-12d3-a456-426614174000.png";

    @InjectMocks
    private AdService adService;

    @Mock
    private AdRepository adRepository;

    @Mock
    private FileStorage fileStorage;

    private static void assertError(Executable executable, AdErrorCode code) {
        assertThatThrownBy(executable::execute)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(code);
    }

    private Ad ad(long id, AdPlacement placement) {
        Ad ad = Ad.create(placement, KEY, "https://example.com", "가을 세일");
        ReflectionTestUtils.setField(ad, "id", id);
        return ad;
    }

    @Test
    @DisplayName("업로드 URL: 5MB 이하 이미지면 ads/ 아래 키를 만든다")
    void createUpload() {
        given(fileStorage.presignUpload(anyString(), eq("image/png"), eq(1000L), any())).willReturn("https://upload");
        given(fileStorage.publicUrl(anyString())).willReturn("https://cdn/ads/x.png");

        AdImageUploadResponse response = adService.createUpload(new AdImageUploadRequest("image/png", 1000L));

        assertThat(response.imageKey()).startsWith("ads/").endsWith(".png");
        assertThat(response.uploadUrl()).isEqualTo("https://upload");
    }

    @Test
    @DisplayName("업로드 URL: 이미지가 아니거나 5MB를 넘으면 거절")
    void createUploadInvalid() {
        assertError(() -> adService.createUpload(new AdImageUploadRequest("application/pdf", 1000L)), AdErrorCode.INVALID_IMAGE);
        assertError(() -> adService.createUpload(new AdImageUploadRequest("image/png", AdService.MAX_IMAGE_BYTES + 1)), AdErrorCode.INVALID_IMAGE);
    }

    @Test
    @DisplayName("등록: 올린 이미지를 확인하고 켜진 광고로 저장한다")
    void create() {
        given(fileStorage.find(KEY)).willReturn(Optional.of(new FileStorage.StoredFile(1000L, "image/png")));
        given(adRepository.save(any(Ad.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(fileStorage.publicUrl(KEY)).willReturn("https://cdn/" + KEY);

        AdResponse response = adService.create(new AdCreateRequest(AdPlacement.HOME_TOP, KEY, " https://example.com/sale ", " 가을 세일 "));

        assertThat(response.enabled()).isTrue();
        assertThat(response.linkUrl()).isEqualTo("https://example.com/sale");
        assertThat(response.altText()).isEqualTo("가을 세일");
        assertThat(response.imageUrl()).isEqualTo("https://cdn/" + KEY);
    }

    @Test
    @DisplayName("등록: 올라가지 않은 이미지거나 다른 곳의 키면 거절")
    void createWithoutImage() {
        given(fileStorage.find(KEY)).willReturn(Optional.empty());

        assertError(() -> adService.create(new AdCreateRequest(AdPlacement.HOME_TOP, KEY, "https://example.com", "광고")), AdErrorCode.IMAGE_NOT_UPLOADED);
        assertError(() -> adService.create(new AdCreateRequest(AdPlacement.HOME_TOP, "products/1/a.png", "https://example.com", "광고")), AdErrorCode.IMAGE_NOT_UPLOADED);
        verify(adRepository, never()).save(any());
    }

    @Test
    @DisplayName("등록: 올라간 파일이 규칙에 안 맞으면 지우고 거절")
    void createWithInvalidFile() {
        given(fileStorage.find(KEY)).willReturn(Optional.of(new FileStorage.StoredFile(AdService.MAX_IMAGE_BYTES + 1, "image/png")));

        assertError(() -> adService.create(new AdCreateRequest(AdPlacement.HOME_TOP, KEY, "https://example.com", "광고")), AdErrorCode.INVALID_IMAGE);
        verify(fileStorage).delete(KEY);
    }

    @Test
    @DisplayName("링크는 http·https만 받는다 (javascript: 같은 주소는 거절)")
    void link() {
        assertThat(AdService.validLink("https://example.com/a?b=1")).isEqualTo("https://example.com/a?b=1");
        assertThat(AdService.validLink("http://example.com")).isEqualTo("http://example.com");
        assertError(() -> AdService.validLink("javascript:alert(1)"), AdErrorCode.INVALID_LINK);
        assertError(() -> AdService.validLink("ftp://example.com"), AdErrorCode.INVALID_LINK);
        assertError(() -> AdService.validLink("example.com"), AdErrorCode.INVALID_LINK);
        assertError(() -> AdService.validLink("https://exa mple.com"), AdErrorCode.INVALID_LINK);
    }

    @Test
    @DisplayName("화면 배너: 켜진 광고만 먼저 등록한 순으로")
    void active() {
        given(adRepository.findByPlacementAndEnabledTrueOrderByIdAsc(AdPlacement.SIDEBAR))
                .willReturn(List.of(ad(1, AdPlacement.SIDEBAR), ad(2, AdPlacement.SIDEBAR)));
        given(fileStorage.publicUrl(KEY)).willReturn("https://cdn/" + KEY);

        assertThat(adService.getActive(AdPlacement.SIDEBAR)).extracting(AdResponse::id).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("켜기·끄기, 없는 광고면 NOT_FOUND")
    void changeEnabled() {
        Ad ad = ad(1, AdPlacement.HOME_TOP);
        given(adRepository.findById(1L)).willReturn(Optional.of(ad));
        given(adRepository.findById(9L)).willReturn(Optional.empty());

        assertThat(adService.changeEnabled(1L, false).enabled()).isFalse();
        assertError(() -> adService.changeEnabled(9L, true), AdErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("삭제하면 이미지도 지운다 (트랜잭션 밖에서는 바로)")
    void delete() {
        Ad ad = ad(1, AdPlacement.HOME_TOP);
        given(adRepository.findById(1L)).willReturn(Optional.of(ad));

        adService.delete(1L);

        verify(adRepository).delete(ad);
        verify(fileStorage).delete(KEY);
    }
}
