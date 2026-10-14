package com.example.ddangddangddang.domain.product.service;

import com.example.ddangddangddang.domain.product.dto.response.ProductResponse;
import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.domain.product.entity.Status;
import com.example.ddangddangddang.domain.product.repository.ProductRepository;
import com.example.ddangddangddang.global.common.ApiResponse;
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
}
