package com.example.ddangddangddang.domain.product.controller;

import com.example.ddangddangddang.domain.product.dto.request.ProductRequest;
import com.example.ddangddangddang.domain.product.dto.request.UpdateRequest;
import com.example.ddangddangddang.domain.product.dto.response.ProductResponse;
import com.example.ddangddangddang.domain.product.service.ProductService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ProductController {
    private final ProductService categoryService;

    // 내 판매 상품 목록 조회(삭제된 상품은 조회X)
    @GetMapping("/users/me/products")
    ResponseEntity<ApiResponse<Page<ProductResponse>>> myProducts(@AuthenticationPrincipal AuthUser authUser,
                                                                  @ParameterObject @PageableDefault(size = 10, sort = "createdAt",
                                                                          direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.myProducts(authUser.id(), pageable)));
    }
    // 상품 상세 조회(삭제된 상품은 조회X, 인증 없이)
    @GetMapping("/products/{productId}")
    ResponseEntity<ApiResponse<ProductResponse>> findAddBy(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.findAddBy(productId)));
    }
    // 상품 생성(판매자는 현재 로그인한 사용자 지정)
    @PostMapping("/products")
    ResponseEntity<ApiResponse<ProductResponse>> creation(@AuthenticationPrincipal AuthUser authUser,
                                                          @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(categoryService.creation(authUser.id(), request)));
    }

    //상품 수정(본인만 수정 가능)
    @PatchMapping("/products/{productId}")
    ResponseEntity<ApiResponse<ProductResponse>> updateProduct(@AuthenticationPrincipal AuthUser authUser,
                                                   @PathVariable Long productId,
                                                   @Valid @RequestBody UpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.updateProduct(authUser.id(),productId, request)));
    }

    //상품 수정(본인만 삭제 가능, 데이터는 남겨두되 조회시 목록에서 제외 )
    @DeleteMapping("/products/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT) // API 명세에 204로 표시하기 위함 (실제 응답은 아래 ResponseEntity)
    ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser authUser,
                                @PathVariable Long productId){
        categoryService.delete(authUser.id(),productId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }


}
