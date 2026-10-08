package com.ownlog.reaction.repository;

import static com.ownlog.post.repository.PostCardQueries.CARD_FIELDS;
import static com.ownlog.post.repository.PostCardQueries.PUBLIC_CONDITION;

import com.ownlog.reaction.domain.Bookmark;
import com.ownlog.reaction.domain.UserPostId;
import com.ownlog.reaction.dto.BookmarkedPostRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BookmarkRepository extends JpaRepository<Bookmark, UserPostId> {

    @Modifying
    @Query(value = """
            INSERT INTO bookmarks (user_id, post_id, created_at) VALUES (:userId, :postId, now())
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(Long userId, Long postId);

    @Modifying
    @Query(value = "DELETE FROM bookmarks WHERE user_id = :userId AND post_id = :postId", nativeQuery = true)
    int deleteIfPresent(Long userId, Long postId);

    // 4.8 내 북마크 목록: 글 카드 목록과 같은 공개 조건(ERD 4.1)과 카드 컬럼을 그대로 써요
    String MY_BOOKMARKS_FROM = """
             from Bookmark bk
             join Post p on p.id = bk.postId
             join p.blog b
             join b.user u
             join p.category c
             left join p.topic t
            """;
    String MY_BOOKMARKS_WHERE = " where bk.userId = :userId and " + PUBLIC_CONDITION;

    @Query(value = "select new com.ownlog.reaction.dto.BookmarkedPostRow(" + CARD_FIELDS + ", bk.createdAt)"
            + MY_BOOKMARKS_FROM + MY_BOOKMARKS_WHERE + " order by bk.createdAt desc, p.id desc",
            countQuery = "select count(bk)" + MY_BOOKMARKS_FROM + MY_BOOKMARKS_WHERE)
    Page<BookmarkedPostRow> findMyBookmarks(Long userId, Pageable pageable);
}
