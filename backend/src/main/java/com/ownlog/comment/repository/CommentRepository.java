package com.ownlog.comment.repository;

import com.ownlog.comment.domain.Comment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * 글의 댓글 전체를 한 번에. 정렬은 SQL이 하고 Java는 묶기만 해요 (ERD 3.6).
     * 작성자를 join fetch로 함께 가져와서 N+1을 막아요.
     * c.parent.id 대신 left join을 써야 부모가 없는 일반 댓글이 빠지지 않아요.
     */
    @Query("""
            select c from Comment c
              join fetch c.user
              left join c.parent p
             where c.post.id = :postId
             order by coalesce(p.id, c.id), p.id nulls first, c.id
            """)
    List<Comment> findAllForPost(Long postId);

    boolean existsByParentId(Long parentId);

    /** 댓글을 실제로 지우기 전에 그 댓글의 신고 기록부터 지워요 (FK, ERD 5장) */
    @Modifying
    @Query(value = "DELETE FROM reports WHERE comment_id = :commentId", nativeQuery = true)
    void deleteReportsOf(Long commentId);
}
