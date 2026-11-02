package com.example.ddangddangddang.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ddangddangddang.domain.auth.repository.LoginAttemptRepository;
import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.domain.category.entity.Category;
import com.example.ddangddangddang.domain.category.repository.CategoryRepository;
import com.example.ddangddangddang.domain.livestream.infra.MediaServerClient;
import com.example.ddangddangddang.domain.product.dto.response.ProductResponse;
import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.domain.product.entity.Status;
import com.example.ddangddangddang.domain.product.repository.ProductRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 상품 목록의 상태·카테고리 거르기 (스튜디오 경매 상품 목록, GET /users/me/products). 실제 쿼리(H2)로 확인한다.
 */
@SpringBootTest
@Transactional
class MyProductsFilterTest {

    @Autowired
    private ProductService productService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;
    @MockitoBean
    private LoginAttemptRepository loginAttemptRepository;
    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;
    @MockitoBean
    private MediaServerClient mediaServerClient;
    @MockitoBean
    private VerificationService verificationService;
    @MockitoBean
    private StompBroadcaster stompBroadcaster;

    private User seller;
    private Category camera;
    private Category film;
    private Category lens;
    private Category clothes;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        seller = userRepository.save(User.create("seller-" + suffix + "@test.com", "pw", "판매" + suffix));
        User other = userRepository.save(User.create("other-" + suffix + "@test.com", "pw", "남" + suffix));
        camera = categoryRepository.save(new Category(null, "카메라" + suffix, 1));
        film = categoryRepository.save(new Category(camera, "필름" + suffix, 1));
        lens = categoryRepository.save(new Category(camera, "렌즈" + suffix, 2));
        Category fashion = categoryRepository.save(new Category(null, "패션" + suffix, 2));
        clothes = categoryRepository.save(new Category(fashion, "옷" + suffix, 1));

        for (int i = 0; i < 12; i++) {
            productRepository.save(new Product(seller, film, "필름 카메라 " + i, null));
        }
        productRepository.save(new Product(seller, lens, "렌즈", null));
        Product sold = new Product(seller, lens, "팔린 렌즈", null);
        sold.sold();
        productRepository.save(sold);
        Product deleted = new Product(seller, clothes, "지운 옷", null);
        deleted.delete();
        productRepository.save(deleted);
        productRepository.save(new Product(seller, clothes, "옷", null));
        productRepository.save(new Product(other, film, "남의 필름 카메라", null));
    }

    private Page<ProductResponse> mine(Long categoryId, Status status, int page) {
        return productService.myProducts(seller.getId(), categoryId, status, PageRequest.of(page, 10, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Test
    @DisplayName("거르지 않으면 내 상품 전부(지운 상품 제외)를 10개씩 나눠 준다")
    void noFilter() {
        Page<ProductResponse> first = mine(null, null, 0);

        assertThat(first.getTotalElements()).isEqualTo(15);
        assertThat(first.getContent()).hasSize(10);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(mine(null, null, 1).getContent()).hasSize(5);
    }

    @Test
    @DisplayName("판매 중(ACTIVE)만 고르면 판매 완료 상품은 빠진다")
    void activeOnly() {
        assertThat(mine(null, Status.ACTIVE, 0).getTotalElements()).isEqualTo(14);
    }

    @Test
    @DisplayName("다른 상품이 쓰는 이미지 키 찾기: 같은 판매자의 다른 상품 갤러리·설명만 본다 (이 상품은 빼고)")
    void imageKeyUsedElsewhere() {
        String key = "product-images/" + seller.getId() + "/0f8fad5b-d9cb-469f-a165-70867728950e.png";
        Product mine = productRepository.save(new Product(seller, film, "이 상품", null));
        mine.changeImages(List.of(key));
        Product other = productRepository.save(new Product(seller, film, "다른 상품", null));
        productRepository.flush();

        // 이 상품만 쓰면 다른 상품에는 없다.
        assertThat(productRepository.existsImageKeyInOtherProduct(seller.getId(), mine.getId(), key)).isFalse();
        assertThat(productRepository.existsKeyInOtherDescription(seller.getId(), mine.getId(), key)).isFalse();

        // 다른 상품 갤러리가 쓰면 찾는다.
        other.changeImages(List.of(key));
        productRepository.flush();
        assertThat(productRepository.existsImageKeyInOtherProduct(seller.getId(), mine.getId(), key)).isTrue();

        // 다른 상품 설명에 들어 있어도 찾는다.
        other.changeImages(List.of());
        other.update(film, "다른 상품", "설명\n![](" + key + ")");
        productRepository.flush();
        assertThat(productRepository.existsImageKeyInOtherProduct(seller.getId(), mine.getId(), key)).isFalse();
        assertThat(productRepository.existsKeyInOtherDescription(seller.getId(), mine.getId(), key)).isTrue();
    }

    @Test
    @DisplayName("소분류를 고르면 그 소분류만, 대분류를 고르면 그 아래 소분류 상품까지 나온다")
    void categoryFilter() {
        assertThat(mine(lens.getId(), Status.ACTIVE, 0).getContent()).extracting(ProductResponse::name).containsExactly("렌즈");
        assertThat(mine(camera.getId(), Status.ACTIVE, 0).getTotalElements()).isEqualTo(13);
        assertThat(mine(clothes.getId(), null, 0).getContent()).extracting(ProductResponse::name).containsExactly("옷");
        assertThat(mine(film.getId(), null, 1).getContent()).hasSize(2);
    }
}
