package com.example.ddangddangddang.domain.category.repository;

import com.example.ddangddangddang.domain.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category,Long> {
    //전체 조회: 정렬 순서
    List<Category> findAllByOrderBySortOrderAscIdAsc();
    //삭제: 하위 카테고리 있을시 삭제 금지
    boolean existsByParentId(Long parentId);

    //대분류 생성시 : 대분류,소분류에 있는지
    boolean existsByName(String name);
    //소분류 생성시 : 같은 이름의 대분류 존재하는지
    boolean existsByParentIsNullAndName(String name);
    //소분류 생성시 : 현재 대분류 중 같은 이름의 소분류 존재 하는지
    boolean existsByParentIdAndName(Long parentId, String name);
}
