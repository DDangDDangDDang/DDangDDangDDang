package com.example.ddangddangddang.domain.product.service;

import com.example.ddangddangddang.domain.category.entity.Category;
import com.example.ddangddangddang.domain.category.repository.CategoryRepository;
import com.example.ddangddangddang.domain.product.dto.request.ProductRequest;
import com.example.ddangddangddang.domain.product.dto.request.UpdateRequest;
import com.example.ddangddangddang.domain.product.dto.response.ProductResponse;
import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.domain.product.entity.Status;
import com.example.ddangddangddang.domain.product.repository.ProductRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
@Transactional
public class ProductService {
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public ProductResponse findAddBy(Long productsId) {
        Product product = productRepository.findByIdAndStatusNot(productsId, Status.DELETED).orElseThrow(() -> new IllegalArgumentException("존재 하지 않은 상품입니다."));

        return ProductResponse.from(product);

    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> myProducts(Long swllerId, Pageable pageable) {
        Page<Product> bySellerIdAndStatusNot = productRepository.findBySellerIdAndStatusNot(swllerId, Status.DELETED, pageable);
        return bySellerIdAndStatusNot.map(ProductResponse::from);
    }


    public ProductResponse creation(Long sellerId, ProductRequest request) {
        User seller = userRepository.findById(sellerId).orElseThrow(
                () -> new IllegalArgumentException("존재하지 않은 회원입니다"));
        Category category = findCategory(request.categoryId());
        Product save = productRepository.save(new Product(seller, category, request.name(), request.description()));

        return ProductResponse.from(save);

    }

    public ProductResponse updateProduct(Long sellerId, Long productsId, UpdateRequest request) {
        Product product = productRepository.findByIdAndStatusNot(productsId, Status.DELETED).orElseThrow(
                () -> new IllegalArgumentException("존재 하지 않은 상품입니다."));

        if (!product.getSeller().getId().equals(sellerId)) {
            throw new IllegalArgumentException("본인 상품만 수정이 가능합니다");
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

    private Category findCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId).orElseThrow(
                () -> new IllegalArgumentException("존재하지 않은 카테고리 입니다")
        );

        if (category.getParent() == null && categoryRepository.existsByParentId(categoryId)) {
            throw new IllegalArgumentException("하위 카테고리를 사용하십쇼");
        }

        return category;
    }
}
