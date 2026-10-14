package com.example.ddangddangddang.domain.product.controller;

import com.example.ddangddangddang.domain.product.dto.request.ProductRequest;
import com.example.ddangddangddang.domain.product.dto.request.UpdateRequest;
import com.example.ddangddangddang.domain.product.dto.response.ProductResponse;
import com.example.ddangddangddang.domain.product.service.ProductService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ProductController {
    private final ProductService categoryService;

    @GetMapping("/users/me/products")
    ResponseEntity<ApiResponse<Page<ProductResponse>>> myProducts(@AuthenticationPrincipal AuthUser authUser,
                                                                  @PageableDefault(size = 10, sort = "createdAt",
                                                                          direction = Sort.Direction.DESC) Pageable pageable){
        return ResponseEntity.ok(ApiResponse.ok(categoryService.myProducts(authUser.id(),pageable)));
    }

    @GetMapping("/products/{productId}")
    ResponseEntity<ApiResponse<ProductResponse>> findAddBy(@PathVariable Long productId){
        return ResponseEntity.ok(ApiResponse.ok(categoryService.findAddBy(productId)));
    }
//
//    @PostMapping("/products")
//    ResponseEntity<ProductResponse> creation(@Valid @RequestBody ProductRequest request){
//        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.creation(request));
//    }
//    @PatchMapping("/products/{productId}")
//    ResponseEntity<ProductResponse> updateCategory(@PathVariable Long categoryId, @Valid @RequestBody UpdateRequest request){
//        return ResponseEntity.ok(categoryService.updateCategory(categoryId,request));
//    }
//
//    @DeleteMapping("/products/{productId}")
//    ResponseEntity<Void> delete(@PathVariable Long categoryId){
//        categoryService.delete(categoryId);
//        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
//    }
//

}
