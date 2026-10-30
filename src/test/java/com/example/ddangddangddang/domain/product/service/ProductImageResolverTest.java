package com.example.ddangddangddang.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.example.ddangddangddang.domain.product.dto.response.ProductImageUploadResponse;
import com.example.ddangddangddang.domain.product.exception.ProductErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.storage.FileStorage;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductImageResolverTest {

    private static final Long SELLER_ID = 7L;
    private static final String KEY_A = "product-images/7/0f8fad5b-d9cb-469f-a165-70867728950e.png";
    private static final String KEY_B = "product-images/7/7c9e6679-7425-40de-944b-e07fc1f90ae7.jpg";

    @InjectMocks
    private ProductImageResolver resolver;

    @Mock
    private FileStorage fileStorage;

    private static String key(int i) {
        return "product-images/7/%08d-0000-0000-0000-000000000000.webp".formatted(i);
    }

    private void uploaded(String key) {
        given(fileStorage.find(key)).willReturn(Optional.of(new FileStorage.StoredFile(1024, "image/png")));
    }

    @Test
    @DisplayName("업로드 URL은 판매자 ID 아래 새 키로, 형식에 맞는 확장자를 붙여 만든다")
    void createUpload() {
        given(fileStorage.presignUpload(anyString(), eq("image/webp"), eq(2048L), any())).willReturn("https://upload");
        given(fileStorage.publicUrl(anyString())).willAnswer(inv -> "https://cdn/" + inv.getArgument(0));

        ProductImageUploadResponse response = resolver.createUpload(SELLER_ID, "image/webp", 2048);

        assertThat(response.imageKey()).matches("product-images/7/[0-9a-f-]{36}\\.webp");
        assertThat(response.uploadUrl()).isEqualTo("https://upload");
        assertThat(response.imageUrl()).isEqualTo("https://cdn/" + response.imageKey());
    }

    @Test
    @DisplayName("이미지가 아닌 형식(PRODUCT_006)과 10MB 넘는 파일(PRODUCT_007)은 업로드 URL을 주지 않는다")
    void createUploadRejected() {
        assertThatThrownBy(() -> resolver.createUpload(SELLER_ID, "image/svg+xml", 100))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProductErrorCode.UNSUPPORTED_IMAGE_TYPE);
        assertThatThrownBy(() -> resolver.createUpload(SELLER_ID, "image/png", 10 * 1024 * 1024 + 1))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProductErrorCode.IMAGE_TOO_LARGE);
        then(fileStorage).should(never()).presignUpload(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("새 이미지는 올라갔는지 확인하고, 순서는 그대로 두되 같은 키는 한 번만 남긴다")
    void resolveImages() {
        uploaded(KEY_A);
        uploaded(KEY_B);

        List<String> keys = resolver.resolveImages(SELLER_ID, Set.of(), List.of(KEY_B, KEY_A, KEY_B));

        assertThat(keys).containsExactly(KEY_B, KEY_A);
    }

    @Test
    @DisplayName("이미 저장된 이미지는 저장소를 다시 확인하지 않는다")
    void savedImageNotRechecked() {
        List<String> keys = resolver.resolveImages(SELLER_ID, Set.of(KEY_A), List.of(KEY_A));

        assertThat(keys).containsExactly(KEY_A);
        then(fileStorage).should(never()).find(anyString());
    }

    @Test
    @DisplayName("남의 키·다른 폴더·이상한 이름·null·올라가지 않은 키는 막는다(PRODUCT_008)")
    void foreignOrMissingKeyRejected() {
        given(fileStorage.find(KEY_A)).willReturn(Optional.empty());
        List<String> bad = java.util.Arrays.asList(
                "product-images/8/0f8fad5b-d9cb-469f-a165-70867728950e.png",
                "profile-images/7/0f8fad5b-d9cb-469f-a165-70867728950e.png",
                "product-images/7/../8/0f8fad5b-d9cb-469f-a165-70867728950e.png",
                "product-images/7/evil.html",
                null,
                KEY_A);
        for (String key : bad) {
            assertThatThrownBy(() -> resolver.resolveImages(SELLER_ID, Set.of(), Collections.singletonList(key)))
                    .as(String.valueOf(key))
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ProductErrorCode.IMAGE_NOT_UPLOADED);
        }
    }

    @Test
    @DisplayName("올라간 파일이 이미지가 아니면(PRODUCT_006), 10MB를 넘으면(PRODUCT_007) 막는다")
    void uploadedFileChecked() {
        given(fileStorage.find(KEY_A)).willReturn(Optional.of(new FileStorage.StoredFile(100, "text/html")));
        assertThatThrownBy(() -> resolver.resolveImages(SELLER_ID, Set.of(), List.of(KEY_A)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProductErrorCode.UNSUPPORTED_IMAGE_TYPE);

        given(fileStorage.find(KEY_B)).willReturn(Optional.of(new FileStorage.StoredFile(10 * 1024 * 1024 + 1, "image/jpeg")));
        assertThatThrownBy(() -> resolver.resolveImages(SELLER_ID, Set.of(), List.of(KEY_B)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProductErrorCode.IMAGE_TOO_LARGE);
    }

    @Test
    @DisplayName("상품 이미지는 10장까지(PRODUCT_009)")
    void tooManyImages() {
        List<String> eleven = IntStream.range(0, 11).mapToObj(ProductImageResolverTest::key).toList();

        assertThatThrownBy(() -> resolver.resolveImages(SELLER_ID, Set.copyOf(eleven), eleven))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProductErrorCode.TOO_MANY_IMAGES);
    }

    @Test
    @DisplayName("설명에서는 한 줄 전체가 ![](키)인 줄만 이미지로 뽑는다. 글 중간·HTML·다른 모양은 글자로 둔다")
    void descriptionImageKeys() {
        String description = String.join("\n",
                "첫 줄 설명",
                "  ![](" + KEY_A + ")  ",
                "글 중간 ![](" + KEY_B + ") 은 글자",
                "<img src=x onerror=alert(1)>",
                "![alt](" + KEY_B + ")",
                "![](" + KEY_B + ")",
                "![](" + KEY_A + ")");

        assertThat(ProductImageResolver.descriptionImageKeys(description)).containsExactly(KEY_A, KEY_B);
        assertThat(ProductImageResolver.descriptionImageKeys(null)).isEmpty();
    }

    @Test
    @DisplayName("설명 속 이미지도 내 키인지, 올라갔는지 확인하고, 20장까지만 받는다(PRODUCT_010)")
    void checkDescription() {
        uploaded(KEY_A);
        resolver.checkDescription(SELLER_ID, Set.of(), "설명\n![](" + KEY_A + ")\n끝");

        assertThatThrownBy(() -> resolver.checkDescription(SELLER_ID, Set.of(),
                "![](https://evil.example/x.png)"))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProductErrorCode.IMAGE_NOT_UPLOADED);

        String many = String.join("\n", IntStream.range(0, 21).mapToObj(i -> "![](" + key(i) + ")").toList());
        assertThatThrownBy(() -> resolver.checkDescription(SELLER_ID, Set.of(), many))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProductErrorCode.TOO_MANY_DESCRIPTION_IMAGES);
    }

    @Test
    @DisplayName("빠진 이미지만 지운다 (트랜잭션 밖이면 바로)")
    void deleteRemoved() {
        resolver.deleteRemovedImagesAfterCommit(SELLER_ID, Set.of(KEY_A, KEY_B), Set.of(KEY_B));

        then(fileStorage).should().delete(KEY_A);
        then(fileStorage).should(never()).delete(KEY_B);
    }

    @Test
    @DisplayName("예전 설명에 남의 키가 적혀 있었어도 그 파일은 지우지 않고, 갤러리로 옮겨 쓸 수도 없다")
    void foreignSavedKeyNeverTrusted() {
        String foreign = "profile-images/8/0f8fad5b-d9cb-469f-a165-70867728950e.jpg";
        Set<String> saved = Set.of(KEY_A, foreign);

        // 설명에 원래 있던 줄은 그대로 저장할 수 있다. (막으면 상품을 아예 수정할 수 없게 된다)
        resolver.checkDescription(SELLER_ID, saved, "![](" + foreign + ")");
        // 하지만 저장돼 있었다는 이유로 갤러리에 넣을 수는 없다.
        assertThatThrownBy(() -> resolver.resolveImages(SELLER_ID, saved, List.of(foreign)))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProductErrorCode.IMAGE_NOT_UPLOADED);
        // 수정에서 그 줄을 빼도 남의 파일은 지우지 않는다.
        resolver.deleteRemovedImagesAfterCommit(SELLER_ID, saved, Set.of());

        then(fileStorage).should().delete(KEY_A);
        then(fileStorage).should(never()).delete(foreign);
        then(fileStorage).should(never()).find(anyString());
    }

    @Test
    @DisplayName("내 키는 판매자 ID 폴더 아래 업로드 URL이 만든 이름만이다")
    void isOwnKey() {
        assertThat(ProductImageResolver.isOwnKey(SELLER_ID, KEY_A)).isTrue();
        assertThat(ProductImageResolver.isOwnKey(8L, KEY_A)).isFalse();
        assertThat(ProductImageResolver.isOwnKey(SELLER_ID, "product-images/7/../8/x.png")).isFalse();
        assertThat(ProductImageResolver.isOwnKey(SELLER_ID, "https://evil.example/x.png")).isFalse();
        assertThat(ProductImageResolver.isOwnKey(SELLER_ID, null)).isFalse();
    }

    @Test
    @DisplayName("상품이 쓰는 이미지는 갤러리와 설명을 합친 것이다")
    void usedKeys() {
        assertThat(ProductImageResolver.usedKeys(List.of(KEY_A), "![](" + KEY_B + ")")).containsExactly(KEY_A, KEY_B);
    }
}
