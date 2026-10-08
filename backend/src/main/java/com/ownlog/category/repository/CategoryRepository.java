package com.ownlog.category.repository;

import com.ownlog.category.domain.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // ---------------------------------------------------------------------------------
    // posts 테이블 대상 SQL: "카테고리 기준" 글 수 세기와 글 이동이라 여기에 둬요.
    // WHERE blog_id = ? AND category_id = ? 형태라 idx_posts_blog_category 인덱스를 써요.
    // ---------------------------------------------------------------------------------

    /** 카테고리별 "발행된" 글 수 (주인용: 비공개 글 포함, 임시저장 제외) */
    @Query(value = """
            SELECT category_id AS "categoryId", COUNT(*) AS "postCount"
              FROM posts
             WHERE blog_id = :blogId AND status = 'PUBLISHED'
             GROUP BY category_id
            """, nativeQuery = true)
    List<CategoryPostCount> countPublishedPosts(Long blogId);

    /** 카테고리별 "방문자가 볼 수 있는" 글 수 (공개 글만. 비공개 카테고리는 서비스에서 통째로 빼요) */
    @Query(value = """
            SELECT category_id AS "categoryId", COUNT(*) AS "postCount"
              FROM posts
             WHERE blog_id = :blogId AND status = 'PUBLISHED' AND visibility = 'PUBLIC'
             GROUP BY category_id
            """, nativeQuery = true)
    List<CategoryPostCount> countPublicPosts(Long blogId);

    /** 카테고리 삭제 전 글 이동 (B-04). 임시저장 글도 함께 옮겨요. 바뀐 행 수를 돌려줘요. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE posts SET category_id = :toCategoryId
             WHERE blog_id = :blogId AND category_id = :fromCategoryId
            """, nativeQuery = true)
    int movePosts(Long blogId, Long fromCategoryId, Long toCategoryId);

    /** 비공개 카테고리 삭제 시: 옮기면서 글을 비공개로 바꿔 숨긴 글이 노출되지 않게 해요 (B-05) */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE posts SET category_id = :toCategoryId, visibility = 'PRIVATE'
             WHERE blog_id = :blogId AND category_id = :fromCategoryId
            """, nativeQuery = true)
    int movePostsAsPrivate(Long blogId, Long fromCategoryId, Long toCategoryId);

    /** 네이티브 쿼리 결과를 받는 인터페이스 (Spring Data가 구현체를 만들어 줘요) */
    interface CategoryPostCount {
        Long getCategoryId();

        Long getPostCount();
    }
}
