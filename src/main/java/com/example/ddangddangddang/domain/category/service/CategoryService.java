package com.example.ddangddangddang.domain.category.service;

import com.example.ddangddangddang.domain.category.dto.request.CategoryRequest;
import com.example.ddangddangddang.domain.category.dto.request.UpdateRequest;
import com.example.ddangddangddang.domain.category.dto.response.CategoryResponse;
import com.example.ddangddangddang.domain.category.entity.Category;
import com.example.ddangddangddang.domain.category.exception.CategoryErrorCode;
import com.example.ddangddangddang.domain.category.repository.CategoryRepository;
import com.example.ddangddangddang.domain.product.entity.Status;
import com.example.ddangddangddang.domain.product.repository.ProductRepository;
import com.example.ddangddangddang.global.config.CacheConfig;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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

    //카테고리 전체 조회
    @Cacheable(cacheNames = CacheConfig.CATEGORIES)
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

    //카테고리 생성
    @CacheEvict(cacheNames = CacheConfig.CATEGORIES, allEntries = true)
    public CategoryResponse creation(CategoryRequest request) {
        Category parent = null;

        if (request.parentId() != null) {
            // 저장된 카테고리중 입력한 카테고리가 있는지 확인(상위,하위 관계없지 검증)
            parent = categoryRepository.findById(request.parentId()).orElseThrow(
                    () -> new BusinessException(CategoryErrorCode.NOT_FOUND));
            // 입력한 상위 카테고리가 진짜 상위 인지 검증
            if (parent.getParent() != null) {
                throw new BusinessException(CategoryErrorCode.PARENT_NOT_TOP_LEVEL);
            }
            if (productRepository.existsByCategoryIdAndStatusNot(parent.getId(), Status.DELETED)){
                throw new BusinessException(CategoryErrorCode.PARENT_HAS_PRODUCTS);
            }
        }
        boolean duplicated;
        if (parent == null) {// 대분류 생성: 소분류의 이름과 같은지
            duplicated = categoryRepository.existsByName(request.name());
        } else {//하위 카테고리 생성: 입력한 카테고리 이름이 상위 카테고리에도 있는지
            duplicated = categoryRepository.existsByParentIsNullAndName(request.name()) || categoryRepository.existsByParentIdAndName(parent.getId(), request.name());

        }
        if (duplicated) {//카테고리 내에 상품이 있는가
            throw new BusinessException(CategoryErrorCode.DUPLICATE_NAME);
        }

        int sortOrder = (request.sortOrder() != null) ? request.sortOrder() : 0;

        Category save = categoryRepository.save(new Category(parent, request.name(), sortOrder));
        return CategoryResponse.from(save);

    }

    //카테고리 수정
    @CacheEvict(cacheNames = CacheConfig.CATEGORIES, allEntries = true)
    public CategoryResponse updateCategory(Long categoryId, UpdateRequest request) {
        Category category = categoryRepository.findById(categoryId).orElseThrow(
                () -> new BusinessException(CategoryErrorCode.NOT_FOUND));

        if (!category.getName().equals(request.name())) {
            boolean duplicated;
            if (category.getParent() == null) {
                duplicated = categoryRepository.existsByName(request.name());

            } else {
                duplicated = categoryRepository.existsByParentIsNullAndName(request.name()) || categoryRepository.existsByParentIdAndName(category.getParent().getId(), request.name());

            }

            if (duplicated) {
                throw new BusinessException(CategoryErrorCode.DUPLICATE_NAME);
            }
        }

        int sortOrder = (request.sortOrder() != null) ? request.sortOrder() : category.getSortOrder();
        category.update(request.name(), sortOrder);

        return CategoryResponse.from(category);
        }

    //카테고리 삭제
    @CacheEvict(cacheNames = CacheConfig.CATEGORIES, allEntries = true)
    public void delete(Long categoryId) {
        Category category = categoryRepository.findById(categoryId).orElseThrow(
                () -> new BusinessException(CategoryErrorCode.NOT_FOUND));

        if (categoryRepository.existsByParentId(categoryId)){
            throw new BusinessException(CategoryErrorCode.HAS_CHILDREN);
        }

        if (productRepository.existsByCategoryId(categoryId)){
            throw new BusinessException(CategoryErrorCode.HAS_PRODUCTS);
        }
        categoryRepository.delete(category);
    }
}
