package com.ownlog.reaction.repository;

import com.ownlog.reaction.domain.PostLike;
import com.ownlog.reaction.domain.UserPostId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PostLikeRepository extends JpaRepository<PostLike, UserPostId> {

    /**
     * 이미 눌렀으면 아무것도 안 해요. 실제로 들어간 줄 수(0 또는 1)를 돌려줘요.
     * 같은 사람이 동시에 두 번 눌러도 두 번째는 첫 번째가 끝날 때까지 기다렸다가 0이 돼요.
     */
    @Modifying
    @Query(value = """
            INSERT INTO post_likes (user_id, post_id, created_at) VALUES (:userId, :postId, now())
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(Long userId, Long postId);

    /** 실제로 지운 줄 수(0 또는 1) */
    @Modifying
    @Query(value = "DELETE FROM post_likes WHERE user_id = :userId AND post_id = :postId", nativeQuery = true)
    int deleteIfPresent(Long userId, Long postId);
}
