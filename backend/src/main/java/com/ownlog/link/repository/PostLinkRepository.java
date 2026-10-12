package com.ownlog.link.repository;

import static com.ownlog.post.repository.PostCardQueries.PUBLIC_CONDITION;

import com.ownlog.link.domain.PostLink;
import com.ownlog.link.domain.PostLinkId;
import com.ownlog.link.dto.LinkedPostRow;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PostLinkRepository extends JpaRepository<PostLink, PostLinkId> {

    /** 이 글이 출발점인 링크 지우기 (수정할 때 다시 계산하려고) */
    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM post_links WHERE source_post_id = :postId", nativeQuery = true)
    void deleteBySource(Long postId);

    /** 글 삭제 시 양쪽 방향 모두 */
    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM post_links WHERE source_post_id = :postId OR target_post_id = :postId", nativeQuery = true)
    void deleteAllOf(Long postId);

    /** 존재하는 "발행 글"만 연결해요 (권한은 읽을 때 검사). 자기 자신은 빼요 */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO post_links (source_post_id, target_post_id, created_at)
            SELECT :sourceId, id, now() FROM posts
             WHERE id IN (:targetIds) AND id <> :sourceId AND status = 'PUBLISHED'
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void insertLinks(Long sourceId, Collection<Long> targetIds);

    /**
     * 13.1 백링크: 이 글을 링크한 글 중 보는 사람이 볼 수 있는 것 (공개 조건 또는 내 글), 최신순.
     * 공개 조건은 PostCardQueries 한 곳에서 가져와요.
     */
    @Query("""
            select new com.ownlog.link.dto.LinkedPostRow(p.id, p.title, u.username, u.nickname, p.publishedAt)
              from PostLink l
              join Post p on p.id = l.sourcePostId
              join p.blog b
              join b.user u
              join p.category c
             where l.targetPostId = :postId
               and p.status = com.ownlog.post.domain.PostStatus.PUBLISHED
               and ((""" + PUBLIC_CONDITION + ") or u.id = :viewerId)"
            + " order by p.publishedAt desc, p.id desc")
    List<LinkedPostRow> findBacklinks(Long postId, Long viewerId, Pageable pageable);
}
