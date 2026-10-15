package com.example.ddangddangddang.domain.product.service;

import com.example.ddangddangddang.domain.category.entity.Category;
import com.example.ddangddangddang.domain.category.exception.CategoryErrorCode;
import com.example.ddangddangddang.domain.category.repository.CategoryRepository;
import com.example.ddangddangddang.domain.product.dto.request.ProductRequest;
import com.example.ddangddangddang.domain.product.dto.request.UpdateRequest;
import com.example.ddangddangddang.domain.product.dto.response.ProductResponse;
import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.domain.product.entity.Status;
import com.example.ddangddangddang.domain.product.exception.ProductErrorCode;
import com.example.ddangddangddang.domain.product.repository.ProductRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Transactional
public class ProductService {
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    //상품 상세 조회(삭제된 상품은 제외)
    @Transactional(readOnly = true)
    public ProductResponse findAddBy(Long productsId) {
        Product product = productRepository.findByIdAndStatusNot(productsId, Status.DELETED).orElseThrow(
                () -> new BusinessException(ProductErrorCode.NOT_FOUND));

        return ProductResponse.from(product);

    }

    //내 상품 조회(삭제된 상품은 제외)
    @Transactional(readOnly = true)
    public Page<ProductResponse> myProducts(Long swllerId, Pageable pageable) {
        Page<Product> bySellerIdAndStatusNot = productRepository.findBySellerIdAndStatusNot(swllerId, Status.DELETED, pageable);
        return bySellerIdAndStatusNot.map(ProductResponse::from);
    }

    //상품 생성(하위 카테고리 내에서만 생성(대분류에는 등록X))
    public ProductResponse creation(Long sellerId, ProductRequest request) {
        User seller = userRepository.findById(sellerId).orElseThrow(
                () -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        Category category = findCategory(request.categoryId());
        Product save = productRepository.save(new Product(seller, category, request.name(), request.description()));

        return ProductResponse.from(save);

    }

    //상품 수정(본인만 수정가능+보내지 않은 값들은 기존 유지)
    public ProductResponse updateProduct(Long sellerId, Long productsId, UpdateRequest request) {
        Product product = productRepository.findByIdAndStatusNot(productsId, Status.DELETED).orElseThrow(
                () -> new BusinessException(ProductErrorCode.NOT_FOUND));

        if (!product.getSeller().getId().equals(sellerId)) {
            throw new BusinessException(ProductErrorCode.NOT_OWNER);
        }

        Category category = (request.categoryId() != null)
                ? findCategory(request.categoryId())
                : product.getCategory();

        String name = (request.name() == null || request.name().isBlank())
                ? product.getName()
                : request.name();
        String description = (request.description() != null)
                ? request.description()
                : product.getDescription();

        product.update(category, name, description);

        return ProductResponse.from(product);

    }
    //카테고리 조회시 하위가 있을시 대분류에 생성 수정시 예외처리
    private Category findCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId).orElseThrow(
                () -> new BusinessException(CategoryErrorCode.NOT_FOUND)
        );

        if (category.getParent() == null && categoryRepository.existsByParentId(categoryId)) {
            throw new BusinessException(ProductErrorCode.SUBCATEGORY_REQUIRED);
        }

        return category;
    }

    //상품 삭제(본인 상품만 삭제 가능+삭제시 Status를 DELETED 전환)
    public void delete(Long sellerId, Long productsId) {
        Product product = productRepository.findByIdAndStatusNot(productsId, Status.DELETED).orElseThrow(
                () -> new BusinessException(ProductErrorCode.NOT_FOUND));
        if (!product.getSeller().getId().equals(sellerId)) {
            throw new BusinessException(ProductErrorCode.NOT_OWNER);
        }
        // 진행 중 경매 있을시 삭제 불가
        product.delete();
    }
}
