package com.example.ddangddangddang.domain.category.dto.response;

import com.example.ddangddangddang.domain.category.entity.Category;

import java.util.List;

public record CategoryResponse(
        Long id,
        String name,
        Integer sortOrder,
        List<CategoryResponse> children
) {
    public static CategoryResponse from(Category category){
        return withChildren(category,List.of());
    }

    public static CategoryResponse withChildren(Category category,List<Category> categoryList){
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSortOrder(),
                categoryList.stream().map(e-> new CategoryResponse(e.getId(),e.getName(),e.getSortOrder(),List.of())).toList()
        );
    }
}
