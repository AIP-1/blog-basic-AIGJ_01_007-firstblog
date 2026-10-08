package com.ownlog.post.repository;

import com.ownlog.post.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PostRepository extends JpaRepository<Post, Long> {

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
