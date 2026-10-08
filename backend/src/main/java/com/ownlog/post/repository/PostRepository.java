package com.ownlog.post.repository;

import static com.ownlog.post.repository.PostCardQueries.COUNT;
import static com.ownlog.post.repository.PostCardQueries.FROM_CARD;
import static com.ownlog.post.repository.PostCardQueries.LATEST;
import static com.ownlog.post.repository.PostCardQueries.PUBLIC_CONDITION;
import static com.ownlog.post.repository.PostCardQueries.SELECT_CARD;

import com.ownlog.post.domain.Post;
import com.ownlog.post.dto.PostCardRow;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PostRepository extends JpaRepository<Post, Long> {

    // ---------------------------------------------------------------------------------
    // 카드 목록 (API 6.1 ~ 6.4). 공개 조건과 SELECT 문은 PostCardQueries 한 곳에서 가져와요.
    // 정렬은 쿼리 안에 고정하고, countQuery는 직접 적어요 (select new 쿼리의 자동 변환을 믿지 않아요).
    // ---------------------------------------------------------------------------------

    /** 6.1 메인 최신 (토픽 필터는 선택) */
    String MAIN_WHERE = " where " + PUBLIC_CONDITION + " and (:topicId is null or t.id = :topicId)";

    @Query(value = SELECT_CARD + FROM_CARD + MAIN_WHERE + LATEST,
            countQuery = COUNT + FROM_CARD + MAIN_WHERE)
    Page<PostCardRow> findLatest(Long topicId, Pageable pageable);

    /** 6.1 메인 인기: 최근 7일 발행 글을 좋아요 순으로 (같으면 최신순) */
    String POPULAR_WHERE = MAIN_WHERE + " and p.publishedAt >= :since";

    @Query(value = SELECT_CARD + FROM_CARD + POPULAR_WHERE + " order by p.likeCount desc, p.publishedAt desc, p.id desc",
            countQuery = COUNT + FROM_CARD + POPULAR_WHERE)
    Page<PostCardRow> findPopular(Long topicId, Instant since, Pageable pageable);

    /** 6.2 검색: 제목 또는 본문, 대소문자 무시. pattern은 서비스에서 %와 _를 이스케이프해서 넘겨요 */
    String SEARCH_WHERE = " where " + PUBLIC_CONDITION
            + " and (lower(p.title) like :pattern escape '\\' or lower(p.content) like :pattern escape '\\')";

    @Query(value = SELECT_CARD + FROM_CARD + SEARCH_WHERE + LATEST,
            countQuery = COUNT + FROM_CARD + SEARCH_WHERE)
    Page<PostCardRow> search(String pattern, Pageable pageable);

    /** 6.3 태그 모아보기 (대소문자 무시) */
    String TAG_FROM = FROM_CARD + " join p.tags tg";
    String TAG_WHERE = " where " + PUBLIC_CONDITION + " and lower(tg.name) = lower(:tagName)";

    @Query(value = SELECT_CARD + TAG_FROM + TAG_WHERE + LATEST,
            countQuery = COUNT + TAG_FROM + TAG_WHERE)
    Page<PostCardRow> findByTag(String tagName, Pageable pageable);

    /** 6.4 블로그 글 목록: 방문자용 (공개 조건 적용) */
    String BLOG_VISITOR_WHERE = " where b.id = :blogId and " + PUBLIC_CONDITION
            + " and (:categoryId is null or c.id = :categoryId)";

    @Query(value = SELECT_CARD + FROM_CARD + BLOG_VISITOR_WHERE + LATEST,
            countQuery = COUNT + FROM_CARD + BLOG_VISITOR_WHERE)
    Page<PostCardRow> findBlogPostsForVisitor(Long blogId, Long categoryId, Pageable pageable);

    /** 6.4 블로그 글 목록: 주인용 (비공개 글, 비공개 카테고리 포함 / 임시저장은 제외) */
    String BLOG_OWNER_WHERE = " where b.id = :blogId"
            + " and p.status = com.ownlog.post.domain.PostStatus.PUBLISHED"
            + " and (:categoryId is null or c.id = :categoryId)";

    @Query(value = SELECT_CARD + FROM_CARD + BLOG_OWNER_WHERE + LATEST,
            countQuery = COUNT + FROM_CARD + BLOG_OWNER_WHERE)
    Page<PostCardRow> findBlogPostsForOwner(Long blogId, Long categoryId, Pageable pageable);

    /** 상세 조회: 글과 함께 필요한 연관 엔티티를 한 번의 쿼리로 가져와요 (N+1 방지) */
    @Query("""
            select p from Post p
              join fetch p.blog b
              join fetch b.user
              join fetch p.category
              left join fetch p.topic
              left join fetch p.tags
             where p.id = :postId
            """)
    Optional<Post> findDetailById(Long postId);

    /** 조회수 +1 (C-04). Java에서 더하지 않고 DB가 직접 더해서 동시 조회에도 정확해요 */
    @Modifying
    @Query(value = "UPDATE posts SET view_count = view_count + 1 WHERE id = :postId", nativeQuery = true)
    void increaseViewCount(Long postId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM post_likes WHERE user_id = :userId AND post_id = :postId)",
            nativeQuery = true)
    boolean existsLike(Long userId, Long postId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM bookmarks WHERE user_id = :userId AND post_id = :postId)",
            nativeQuery = true)
    boolean existsBookmark(Long userId, Long postId);

    // ---------------------------------------------------------------------------------
    // 글 삭제 시 함께 지울 데이터 (ERD 5장). 댓글·좋아요·북마크·신고 엔티티가 아직 없어서 SQL로 지워요.
    // FK에 CASCADE를 쓰지 않으니 "참조하는 쪽"부터 지워야 해요.
    // ---------------------------------------------------------------------------------

    /** 글 신고 + 이 글에 달린 댓글들의 신고 */
    @Modifying
    @Query(value = """
            DELETE FROM reports
             WHERE post_id = :postId
                OR comment_id IN (SELECT id FROM comments WHERE post_id = :postId)
            """, nativeQuery = true)
    void deleteReports(Long postId);

    /** 댓글과 답글 (자기 참조 FK는 문장이 끝날 때 검사해서 한 번에 지울 수 있어요) */
    @Modifying
    @Query(value = "DELETE FROM comments WHERE post_id = :postId", nativeQuery = true)
    void deleteComments(Long postId);

    @Modifying
    @Query(value = "DELETE FROM post_likes WHERE post_id = :postId", nativeQuery = true)
    void deleteLikes(Long postId);

    @Modifying
    @Query(value = "DELETE FROM bookmarks WHERE post_id = :postId", nativeQuery = true)
    void deleteBookmarks(Long postId);
}
