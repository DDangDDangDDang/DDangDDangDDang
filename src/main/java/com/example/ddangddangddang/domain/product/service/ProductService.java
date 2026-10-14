package com.example.ddangddangddang.domain.product.service;

import com.example.ddangddangddang.domain.category.entity.Category;
import com.example.ddangddangddang.domain.category.repository.CategoryRepository;
import com.example.ddangddangddang.domain.product.dto.request.ProductRequest;
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
        Category category = categoryRepository.findById(request.categoryId()).orElseThrow(
                () -> new IllegalArgumentException("존재하지 않은 카테고리입니다"));
        if (category.getParent() == null && categoryRepository.existsByParentId(category.getId())){
            throw new IllegalArgumentException("하위 카테고리 선택 해주세요");
        }
        Product save = productRepository.save(new Product(seller, category, request.name(), request.description()));

        return ProductResponse.from(save);

    }
}
