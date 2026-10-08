package com.ownlog.category.service;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import com.ownlog.blog.domain.Blog;
import com.ownlog.blog.service.BlogService;
import com.ownlog.category.domain.Category;
import com.ownlog.category.dto.CategoryCreateRequest;
import com.ownlog.category.dto.CategoryDeleteResponse;
import com.ownlog.category.dto.CategoryItem;
import com.ownlog.category.dto.CategoryOrderRequest;
import com.ownlog.category.dto.CategorySidebarResponse;
import com.ownlog.category.dto.CategoryUpdateRequest;
import com.ownlog.category.repository.CategoryRepository;
import com.ownlog.category.repository.CategoryRepository.CategoryPostCount;
import com.ownlog.global.common.Visibility;
import com.ownlog.global.error.BusinessException;
import com.ownlog.global.error.ErrorCode;
import com.ownlog.global.security.LoginUser;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final BlogService blogService;

    /** API 5.3 사이드바. 방문자에게는 비공개 카테고리를 빼고, 볼 수 있는 글만 세요. */
    @Transactional(readOnly = true)
    public CategorySidebarResponse getSidebar(String username, LoginUser me) {
        Blog blog = blogService.getActiveBlog(username);
        boolean isOwner = blogService.isOwner(blog, me);

        Map<Long, Long> counts = (isOwner
                ? categoryRepository.countPublishedPosts(blog.getId())
                : categoryRepository.countPublicPosts(blog.getId()))
                .stream()
                .collect(toMap(CategoryPostCount::getCategoryId, CategoryPostCount::getPostCount));

        List<CategoryItem> items = categoryRepository.findAllByBlogIdOrderByDisplayOrderAscIdAsc(blog.getId())
                .stream()
                .filter(category -> isOwner || !category.isPrivate())
                .map(category -> CategoryItem.of(category, counts.getOrDefault(category.getId(), 0L)))
                .toList();

        long total = items.stream().mapToLong(CategoryItem::postCount).sum();
        return new CategorySidebarResponse(total, items);
    }

    /** API 5.4 생성: 최대 30개, 이름 중복 불가, 맨 아래에 추가 */
    @Transactional
    public CategoryItem create(Long blogId, LoginUser me, CategoryCreateRequest request) {
        Blog blog = blogService.getOwnedBlog(blogId, me);
        String name = request.name().strip();

        if (categoryRepository.countByBlogId(blogId) >= Category.MAX_COUNT_PER_BLOG) {
            throw new BusinessException(ErrorCode.CATEGORY_LIMIT_EXCEEDED);
        }
        if (categoryRepository.existsByBlogIdAndName(blogId, name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_CATEGORY_NAME);
        }
        int order = categoryRepository.findMaxDisplayOrder(blogId) + 1;
        Category saved = categoryRepository.save(
                Category.create(blog, name, order, Visibility.orPublic(request.visibility())));
        return CategoryItem.of(saved, 0L);
    }

    /** API 5.5 순서 변경: 블로그의 모든 카테고리 id가 빠짐없이, 중복 없이 와야 해요 */
    @Transactional
    public void reorder(Long blogId, LoginUser me, CategoryOrderRequest request) {
        blogService.getOwnedBlog(blogId, me);
        List<Category> categories = categoryRepository.findAllByBlogIdOrderByDisplayOrderAscIdAsc(blogId);
        List<Long> ids = request.categoryIds();

        Map<Long, Category> byId = categories.stream().collect(toMap(Category::getId, identity()));
        if (ids.size() != categories.size() || !byId.keySet().equals(new HashSet<>(ids))) {
            throw new BusinessException(ErrorCode.INVALID_CATEGORY_ORDER);
        }
        for (int i = 0; i < ids.size(); i++) {
            byId.get(ids.get(i)).changeOrder(i);   // 트랜잭션이 끝날 때 바뀐 것만 UPDATE돼요 (변경 감지)
        }
    }

    /** API 5.6 이름·공개 범위 수정 */
    @Transactional
    public CategoryItem update(Long categoryId, LoginUser me, CategoryUpdateRequest request) {
        Category category = getOwnedCategory(categoryId, me);
        String name = request.name().strip();
        if (!name.equals(category.getName())
                && categoryRepository.existsByBlogIdAndName(category.getBlog().getId(), name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_CATEGORY_NAME);
        }
        category.update(name, request.visibility());
        return CategoryItem.of(category, countPublished(category));
    }

    /** API 5.7 삭제: 글을 "미분류"로 옮긴 뒤 삭제. 비공개 카테고리였다면 옮기는 글을 비공개로 */
    @Transactional
    public CategoryDeleteResponse delete(Long categoryId, LoginUser me) {
        Category category = getOwnedCategory(categoryId, me);
        category.validateDeletable();

        Long blogId = category.getBlog().getId();
        Category defaultCategory = categoryRepository.findByBlogIdAndIsDefaultTrue(blogId)
                .orElseThrow(() -> new IllegalStateException("기본 카테고리가 없는 블로그: " + blogId));

        int moved = category.isPrivate()
                ? categoryRepository.movePostsAsPrivate(blogId, categoryId, defaultCategory.getId())
                : categoryRepository.movePosts(blogId, categoryId, defaultCategory.getId());
        categoryRepository.delete(category);

        return new CategoryDeleteResponse(moved, defaultCategory.getId());
    }

    /** 글·임시저장 글에 지정할 카테고리. null이면 "미분류", 내 블로그의 카테고리가 아니면 INVALID_CATEGORY */
    public Category getCategoryForPost(Blog blog, Long categoryId) {
        if (categoryId == null) {
            return categoryRepository.findByBlogIdAndIsDefaultTrue(blog.getId())
                    .orElseThrow(() -> new IllegalStateException("기본 카테고리가 없는 블로그: " + blog.getId()));
        }
        return categoryRepository.findById(categoryId)
                .filter(category -> category.getBlog().getId().equals(blog.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CATEGORY));
    }

    /** 남의 공개 카테고리 → 403, 남의 비공개 카테고리 → 404 (존재 여부를 숨겨요) */
    private Category getOwnedCategory(Long categoryId, LoginUser me) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        if (!category.getBlog().isOwnedBy(me.id())) {
            throw new BusinessException(category.isPrivate()
                    ? ErrorCode.CATEGORY_NOT_FOUND : ErrorCode.FORBIDDEN);
        }
        return category;
    }

    private long countPublished(Category category) {
        return categoryRepository.countPublishedPosts(category.getBlog().getId()).stream()
                .filter(count -> count.getCategoryId().equals(category.getId()))
                .mapToLong(CategoryPostCount::getPostCount)
                .findFirst()
                .orElse(0L);
    }
}
