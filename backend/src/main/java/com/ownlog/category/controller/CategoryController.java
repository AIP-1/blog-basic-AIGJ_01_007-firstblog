package com.ownlog.category.controller;

import com.ownlog.category.dto.CategoryCreateRequest;
import com.ownlog.category.dto.CategoryDeleteResponse;
import com.ownlog.category.dto.CategoryItem;
import com.ownlog.category.dto.CategoryOrderRequest;
import com.ownlog.category.dto.CategorySidebarResponse;
import com.ownlog.category.dto.CategoryUpdateRequest;
import com.ownlog.category.service.CategoryService;
import com.ownlog.global.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /** API 5.3 카테고리 사이드바 (🔓) */
    @GetMapping("/api/blogs/{username}/categories")
    public CategorySidebarResponse getSidebar(@PathVariable String username, @AuthenticationPrincipal LoginUser me) {
        return categoryService.getSidebar(username, me);
    }

    /** API 5.4 카테고리 생성 (👤) → 201 */
    @PostMapping("/api/blogs/{blogId}/categories")
    public ResponseEntity<CategoryItem> create(@PathVariable Long blogId, @AuthenticationPrincipal LoginUser me,
                                               @Valid @RequestBody CategoryCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(blogId, me, request));
    }

    /** API 5.5 카테고리 순서 변경 (👤) → 204 */
    @PutMapping("/api/blogs/{blogId}/categories/order")
    public ResponseEntity<Void> reorder(@PathVariable Long blogId, @AuthenticationPrincipal LoginUser me,
                                        @Valid @RequestBody CategoryOrderRequest request) {
        categoryService.reorder(blogId, me, request);
        return ResponseEntity.noContent().build();
    }

    /** API 5.6 카테고리 이름·공개 범위 수정 (👤) */
    @PutMapping("/api/categories/{categoryId}")
    public CategoryItem update(@PathVariable Long categoryId, @AuthenticationPrincipal LoginUser me,
                               @Valid @RequestBody CategoryUpdateRequest request) {
        return categoryService.update(categoryId, me, request);
    }

    /** API 5.7 카테고리 삭제 (👤) */
    @DeleteMapping("/api/categories/{categoryId}")
    public CategoryDeleteResponse delete(@PathVariable Long categoryId, @AuthenticationPrincipal LoginUser me) {
        return categoryService.delete(categoryId, me);
    }
}
