package com.example.ddangddangddang.domain.category.service;

import com.example.ddangddangddang.domain.category.dto.request.CategoryRequest;
import com.example.ddangddangddang.domain.category.dto.request.UpdateRequest;
import com.example.ddangddangddang.domain.category.dto.response.CategoryResponse;
import com.example.ddangddangddang.domain.category.entity.Category;
import com.example.ddangddangddang.domain.category.repository.CategoryRepository;
import com.example.ddangddangddang.domain.product.repository.ProductRepository;
import com.example.ddangddangddang.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
@Transactional
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    @Transactional(readOnly = true)
    public List<CategoryResponse> findAdd() {
        List<Category> all = categoryRepository.findAllByOrderBySortOrderAscIdAsc();
        // 저장된 카테고리 불러 온다
        ArrayList<CategoryResponse> responses = new ArrayList<>();
        //불러온 카테고리를 담는 저장소 생성

        for (Category parent : all) {
            if (parent.getParent() != null) {
                continue;
            }
            //부모 카테고리 있는지 검증(없을시 반복문에서 건너뛰기)


            ArrayList<Category> children = new ArrayList<>();
            //자식 카테고리 담는 저장소
            for (Category category : all) {
                if (category.getParent() != null && category.getParent().getId().equals(parent.getId())) {
                    children.add(category);
                }
            }
            //부모 카테고리 있는지 재 검증 및 현재 검증중인 부모 카테고와 같은지 검증 후 자식 카테고리 저장소의 저장
            responses.add(CategoryResponse.withChildren(parent, children));
            // 대분류+하위 합치기
        }
        return responses;
    }


    public CategoryResponse creation(CategoryRequest request) {
        Category parent = null;

        if (request.parentId() != null) {
            // 저장된 카테고리중 입력한 카테고리가 있는지 확인(상위,하위 관계없지 검증)
            parent = categoryRepository.findById(request.parentId()).orElseThrow(() -> new IllegalArgumentException("저장된 상위 카테고리 내에 없습니다"));
            // 입력한 상위 카테고리가 진짜 상위 인지 검증
            if (parent.getParent() != null) {
                throw new IllegalArgumentException("상위 카테고리는 대분류만 선택 가능합니다.");

            }
        }
        boolean duplicated;
        if (parent == null) {// 대분류 생성: 소분류의 이름과 같은지
            duplicated = categoryRepository.existsByName(request.name());
        } else {//하위 카테고리 생성: 입력한 카테고리 이름이 상위 카테고리에도 있는지
            duplicated = categoryRepository.existsByParentIsNullAndName(request.name()) || categoryRepository.existsByParentIdAndName(parent.getId(), request.name());

        }
        if (duplicated) {
            throw new IllegalArgumentException("이미 같은 이름의 카테고리가 있습니다");
        }

        int sortOrder = (request.sortOrder() != null) ? request.sortOrder() : 0;

        Category save = categoryRepository.save(new Category(parent, request.name(), sortOrder));
        return CategoryResponse.from(save);

    }

    public CategoryResponse updateCategory(Long categoryId, UpdateRequest request) {
        Category category = categoryRepository.findById(categoryId).orElseThrow(() -> new IllegalArgumentException("카테고리가 존재 하지 않습니다"));

        if (!category.getName().equals(request.name())) {
            boolean duplicated;
            if (category.getParent() == null) {
                duplicated = categoryRepository.existsByName(request.name());

            } else {
                duplicated = categoryRepository.existsByParentIsNullAndName(request.name()) || categoryRepository.existsByParentIdAndName(category.getParent().getId(), request.name());

            }

            if (duplicated) {
                throw new IllegalArgumentException("이미 같은 이름의 카테고리가 있습니다");
            }
        }

        int sortOrder = (request.sortOrder() != null) ? request.sortOrder() : category.getSortOrder();
        category.update(request.name(), sortOrder);

        return CategoryResponse.from(category);
        }

    public void delete(Long categoryId) {
        Category category = categoryRepository.findById(categoryId).orElseThrow(
                () -> new IllegalArgumentException("카테고리가 존재 하지 않습니다"));

        if (categoryRepository.existsByParentId(categoryId)){
            throw new IllegalArgumentException("하위 카테고리가 있어 삭제 불가능합니다.");
        }

        if (productRepository.existsByCategoryId(categoryId)){
            throw new IllegalArgumentException("이 카테고리에서 사용하는 상품이 있어 삭제 불가능 합니다.");
        }
        categoryRepository.delete(category);
    }
}
