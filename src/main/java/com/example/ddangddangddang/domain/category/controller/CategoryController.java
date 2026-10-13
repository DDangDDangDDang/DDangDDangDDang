package com.example.ddangddangddang.domain.category.controller;

import com.example.ddangddangddang.domain.category.dto.request.CategoryRequest;
import com.example.ddangddangddang.domain.category.dto.request.UpdateRequest;
import com.example.ddangddangddang.domain.category.dto.response.CategoryResponse;
import com.example.ddangddangddang.domain.category.service.CategoryService;
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

    @GetMapping("/categories")
    ResponseEntity<List<CategoryResponse>> findAdd(){
        return ResponseEntity.ok(categoryService.findAdd());
    }
    @PostMapping("/admin/categories")
    ResponseEntity<CategoryResponse> creation(@Valid @RequestBody CategoryRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.creation(request));
    }
    @PatchMapping("/admin/categories/{categoryId}")
    ResponseEntity<CategoryResponse> updateCategory(@PathVariable Long categoryId, @Valid @RequestBody UpdateRequest request){
        return ResponseEntity.ok(categoryService.updateCategory(categoryId,request));
    }

    @DeleteMapping("/admin/categories/{categoryId}")
    ResponseEntity<Void> delete(@PathVariable Long categoryId){
        categoryService.delete(categoryId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }


}
