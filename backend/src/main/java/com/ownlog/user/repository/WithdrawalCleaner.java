package com.ownlog.user.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 탈퇴할 때 지울 데이터 (ERD 5장). 여러 테이블에 걸친 일괄 삭제라 SQL로 순서대로 지워요.
 * FK에 CASCADE가 없어서 "참조하는 쪽"부터 지워야 해요.
 * JdbcTemplate은 JPA 트랜잭션과 같은 DB 연결을 써서, 서비스의 @Transactional 하나로 함께 커밋·롤백돼요.
 */
@Component
@RequiredArgsConstructor
public class WithdrawalCleaner {

    private static final String MY_POSTS =
            "SELECT p.id FROM posts p JOIN blogs b ON b.id = p.blog_id WHERE b.user_id = ?";

    private final JdbcTemplate jdbcTemplate;

    /** ① 내 블로그 전부 (다중 블로그가 되어도 그대로 동작해요): 글에 딸린 것 → 글 → 카테고리 → 블로그 */
    public void deleteBlogsOf(Long userId) {
        jdbcTemplate.update("DELETE FROM reports WHERE post_id IN (" + MY_POSTS + ")"
                + " OR comment_id IN (SELECT id FROM comments WHERE post_id IN (" + MY_POSTS + "))", userId, userId);
        jdbcTemplate.update("DELETE FROM comments WHERE post_id IN (" + MY_POSTS + ")", userId);
        jdbcTemplate.update("DELETE FROM post_likes WHERE post_id IN (" + MY_POSTS + ")", userId);
        jdbcTemplate.update("DELETE FROM bookmarks WHERE post_id IN (" + MY_POSTS + ")", userId);
        jdbcTemplate.update("DELETE FROM post_tags WHERE post_id IN (" + MY_POSTS + ")", userId);
        jdbcTemplate.update("DELETE FROM post_links WHERE source_post_id IN (" + MY_POSTS + ")"
                + " OR target_post_id IN (" + MY_POSTS + ")", userId, userId);
        jdbcTemplate.update("DELETE FROM posts WHERE blog_id IN (SELECT id FROM blogs WHERE user_id = ?)", userId);
        jdbcTemplate.update("DELETE FROM categories WHERE blog_id IN (SELECT id FROM blogs WHERE user_id = ?)", userId);
        jdbcTemplate.update("DELETE FROM blogs WHERE user_id = ?", userId);
    }

    /** ② 남의 글에 누른 내 좋아요: 먼저 그 글들의 좋아요 수를 줄이고(어떤 글인지 알아야 하니까), 그다음 지워요 */
    public void deleteLikesOf(Long userId) {
        jdbcTemplate.update("""
                UPDATE posts SET like_count = like_count - 1
                 WHERE id IN (SELECT post_id FROM post_likes WHERE user_id = ?) AND like_count > 0
                """, userId);
        jdbcTemplate.update("DELETE FROM post_likes WHERE user_id = ?", userId);
    }

    /** ③ 내 북마크 */
    public void deleteBookmarksOf(Long userId) {
        jdbcTemplate.update("DELETE FROM bookmarks WHERE user_id = ?", userId);
    }
}
