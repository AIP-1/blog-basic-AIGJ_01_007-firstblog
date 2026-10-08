package com.ownlog.category.repository;

import com.ownlog.category.domain.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** 사이드바 (B-02) */
    List<Category> findAllByBlogIdOrderByDisplayOrderAscIdAsc(Long blogId);

    /** "미분류" 찾기 */
    Optional<Category> findByBlogIdAndIsDefaultTrue(Long blogId);

    /** 이름 중복 (B-04) */
    boolean existsByBlogIdAndName(Long blogId, String name);

    /** 30개 제한 (B-04) */
    long countByBlogId(Long blogId);

    /** 새 카테고리는 맨 아래에 추가해요 */
    @Query("select coalesce(max(c.displayOrder), -1) from Category c where c.blog.id = :blogId")
    int findMaxDisplayOrder(Long blogId);
}
