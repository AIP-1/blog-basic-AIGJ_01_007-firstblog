package com.ownlog.category.domain;

import com.ownlog.blog.domain.Blog;
import com.ownlog.global.common.Visibility;
import com.ownlog.global.entity.BaseTimeEntity;
import com.ownlog.global.error.BusinessException;
import com.ownlog.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 카테고리 (ERD 3.3). 블로그마다 기본 카테고리 "미분류"가 정확히 1개 있어요. */
@Getter
@Entity
@Table(name = "categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseTimeEntity {

    public static final String DEFAULT_NAME = "미분류";
    public static final int MAX_COUNT_PER_BLOG = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blog_id", nullable = false)
    private Blog blog;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(nullable = false)
    private int displayOrder;

    @Column(name = "is_default", nullable = false, updatable = false)  // 생성 후 바꿀 수 없어요
    private boolean isDefault;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility;

    public static Category createDefault(Blog blog) {
        Category category = create(blog, DEFAULT_NAME, 0, Visibility.PUBLIC);
        category.isDefault = true;
        return category;
    }

    public static Category create(Blog blog, String name, int displayOrder, Visibility visibility) {
        Category category = new Category();
        category.blog = blog;
        category.name = name;
        category.displayOrder = displayOrder;
        category.visibility = visibility;
        return category;
    }

    public void update(String name, Visibility visibility) {
        this.name = name;
        this.visibility = visibility;
    }

    public void changeOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public void validateDeletable() {
        if (isDefault) {
            throw new BusinessException(ErrorCode.DEFAULT_CATEGORY_NOT_DELETABLE);
        }
    }

    public boolean isPrivate() {
        return visibility == Visibility.PRIVATE;
    }
}
