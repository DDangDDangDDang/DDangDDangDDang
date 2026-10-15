package com.example.ddangddangddang.domain.category.controller;

import com.example.ddangddangddang.domain.category.dto.request.CategoryRequest;
import com.example.ddangddangddang.domain.category.dto.request.UpdateRequest;
import com.example.ddangddangddang.domain.category.dto.response.CategoryResponse;
import com.example.ddangddangddang.domain.category.service.CategoryService;
import com.example.ddangddangddang.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class CategoryController {
    private final CategoryService categoryService;

    //카테고리 전체 조회
    @GetMapping("/categories")
    ResponseEntity<ApiResponse<List<CategoryResponse>>> findAdd(){
        return ResponseEntity.ok(ApiResponse.ok(categoryService.findAdd()));
    }

    //카테고리 생성(관리자만)
    @PostMapping("/admin/categories")
    ResponseEntity<ApiResponse<CategoryResponse>> creation(@Valid @RequestBody CategoryRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(categoryService.creation(request)));
    }

    //카테고리 수정(관리자만)
    @PatchMapping("/admin/categories/{categoryId}")
    ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(@PathVariable Long categoryId, @Valid @RequestBody UpdateRequest request){
        return ResponseEntity.ok(ApiResponse.ok(categoryService.updateCategory(categoryId,request)));
    }

    //카테고리 삭제(관리자만)
    @DeleteMapping("/admin/categories/{categoryId}")
    ResponseEntity<Void> delete(@PathVariable Long categoryId){
        categoryService.delete(categoryId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }


}
