package com.example.ddangddangddang.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.category.entity.Category;
import com.example.ddangddangddang.domain.category.repository.CategoryRepository;
import com.example.ddangddangddang.domain.product.dto.request.ProductRequest;
import com.example.ddangddangddang.domain.product.dto.request.UpdateRequest;
import com.example.ddangddangddang.domain.product.dto.response.ProductImageResponse;
import com.example.ddangddangddang.domain.product.dto.response.ProductResponse;
import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.domain.product.entity.Status;
import com.example.ddangddangddang.domain.product.exception.ProductErrorCode;
import com.example.ddangddangddang.domain.product.repository.ProductRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.storage.FileStorage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceTest {

    private static final Long SELLER_ID = 7L;
    private static final Long PRODUCT_ID = 30L;
    private static final Long CATEGORY_ID = 3L;
    private static final String KEY_A = "product-images/7/0f8fad5b-d9cb-469f-a165-70867728950e.png";
    private static final String KEY_B = "product-images/7/7c9e6679-7425-40de-944b-e07fc1f90ae7.jpg";
    private static final String KEY_C = "product-images/7/16fd2706-8baf-433b-82eb-8c7fada847da.webp";

    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private FileStorage fileStorage;

    private ProductService productService;
    private User seller;
    private Category category;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, userRepository, categoryRepository, auctionRepository,
                new ProductImageResolver(fileStorage), fileStorage);
        seller = User.create("seller@test.com", "pw", "seller");
        ReflectionTestUtils.setField(seller, "id", SELLER_ID);
        category = new Category(null, "카메라", 1);
        ReflectionTestUtils.setField(category, "id", CATEGORY_ID);

        given(userRepository.findById(SELLER_ID)).willReturn(Optional.of(seller));
        given(categoryRepository.findById(CATEGORY_ID)).willReturn(Optional.of(category));
        given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));
        given(fileStorage.publicUrl(anyString())).willAnswer(inv -> "https://cdn/" + inv.getArgument(0));
        given(fileStorage.find(anyString())).willReturn(Optional.of(new FileStorage.StoredFile(1024, "image/png")));
    }

    private Product savedProduct(List<String> imageKeys, String description) {
        Product product = new Product(seller, category, "필름 카메라", description);
        product.changeImages(imageKeys);
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
        given(productRepository.findWithLockByIdAndStatusNot(PRODUCT_ID, Status.DELETED)).willReturn(Optional.of(product));
        return product;
    }

    @Test
    @DisplayName("이미지와 함께 등록하면 첫 장이 대표 이미지가 되고, 설명 속 이미지도 주소와 함께 내려준다")
    void createWithImages() {
        String description = "렌즈 깨끗해요\n![](" + KEY_C + ")\n구성품 전부";

        ProductResponse response = productService.creation(SELLER_ID,
                new ProductRequest(CATEGORY_ID, "필름 카메라", description, List.of(KEY_B, KEY_A)));

        assertThat(response.thumbnailUrl()).isEqualTo("https://cdn/" + KEY_B);
        assertThat(response.images()).extracting(ProductImageResponse::key).containsExactly(KEY_B, KEY_A);
        assertThat(response.descriptionImages()).containsExactly(new ProductImageResponse(KEY_C, "https://cdn/" + KEY_C));
        assertThat(response.description()).isEqualTo(description);
    }

    @Test
    @DisplayName("이미지 없이 등록하면 대표 이미지도 없다 (기존 요청과 같다)")
    void createWithoutImages() {
        ProductResponse response = productService.creation(SELLER_ID, new ProductRequest(CATEGORY_ID, "필름 카메라", null, null));

        assertThat(response.thumbnailUrl()).isNull();
        assertThat(response.images()).isEmpty();
        assertThat(response.descriptionImages()).isEmpty();
    }

    @Test
    @DisplayName("남의 이미지 키로는 등록할 수 없다(PRODUCT_008)")
    void createWithForeignKeyRejected() {
        String foreign = "product-images/8/0f8fad5b-d9cb-469f-a165-70867728950e.png";

        assertThatThrownBy(() -> productService.creation(SELLER_ID,
                new ProductRequest(CATEGORY_ID, "필름 카메라", "![](" + foreign + ")", List.of())))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProductErrorCode.IMAGE_NOT_UPLOADED);
        then(productRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("수정에서 순서를 바꾸고 이미지를 빼면 대표 이미지가 바뀌고, 빠진 이미지만 지운다. 저장된 이미지는 다시 확인하지 않는다")
    void updateReorderAndRemove() {
        Product product = savedProduct(List.of(KEY_A, KEY_B), "설명\n![](" + KEY_C + ")");

        productService.updateProduct(SELLER_ID, PRODUCT_ID, new UpdateRequest(null, null, "설명만", List.of(KEY_B)));

        assertThat(product.getImageKeys()).containsExactly(KEY_B);
        assertThat(product.getThumbnailKey()).isEqualTo(KEY_B);
        then(fileStorage).should().delete(KEY_A);
        then(fileStorage).should().delete(KEY_C);
        then(fileStorage).should(never()).delete(KEY_B);
        then(fileStorage).should(never()).find(anyString());
    }

    @Test
    @DisplayName("갤러리에서 뺀 이미지라도 설명에 남아 있으면 지우지 않는다")
    void imageMovedToDescriptionKept() {
        savedProduct(List.of(KEY_A), "설명");

        productService.updateProduct(SELLER_ID, PRODUCT_ID, new UpdateRequest(null, null, "![](" + KEY_A + ")", List.of()));

        then(fileStorage).should(never()).delete(anyString());
    }

    @Test
    @DisplayName("imageKeys를 보내지 않으면 이미지는 그대로 둔다")
    void updateWithoutImageKeysKeepsImages() {
        Product product = savedProduct(List.of(KEY_A, KEY_B), "설명");

        productService.updateProduct(SELLER_ID, PRODUCT_ID, new UpdateRequest(null, "새 이름", null, null));

        assertThat(product.getImageKeys()).containsExactly(KEY_A, KEY_B);
        assertThat(product.getName()).isEqualTo("새 이름");
        then(fileStorage).should(never()).delete(anyString());
    }

    @Test
    @DisplayName("판매된 상품은 주문이 대표 이미지를 쓰고 있어 빠진 이미지를 지우지 않는다")
    void soldProductImagesKept() {
        Product product = savedProduct(List.of(KEY_A), "설명");
        product.sold();

        productService.updateProduct(SELLER_ID, PRODUCT_ID, new UpdateRequest(null, null, null, List.of()));

        assertThat(product.getThumbnailKey()).isNull();
        then(fileStorage).should(never()).delete(anyString());
    }

    @Test
    @DisplayName("새로 넣은 이미지가 올라가지 않았으면 수정을 막고 아무것도 지우지 않는다")
    void updateWithMissingImageRejected() {
        Product product = savedProduct(List.of(KEY_A), "설명");
        given(fileStorage.find(KEY_B)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(SELLER_ID, PRODUCT_ID, new UpdateRequest(null, null, null, List.of(KEY_B))))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProductErrorCode.IMAGE_NOT_UPLOADED);
        assertThat(product.getImageKeys()).containsExactly(KEY_A);
        then(fileStorage).should(never()).delete(anyString());
    }

    @Test
    @DisplayName("업로드 URL은 로그인한 사용자(판매자) ID 아래에 만든다")
    void createImageUpload() {
        given(fileStorage.presignUpload(anyString(), anyString(), anyLong(), any())).willReturn("https://upload");

        var response = productService.createImageUpload(SELLER_ID,
                new com.example.ddangddangddang.domain.product.dto.request.ProductImageUploadRequest("image/jpeg", 500L));

        assertThat(response.imageKey()).startsWith("product-images/7/").endsWith(".jpg");
    }
}
