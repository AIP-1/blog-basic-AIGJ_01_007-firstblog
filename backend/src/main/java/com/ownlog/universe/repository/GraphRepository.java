package com.ownlog.universe.repository;

import static com.ownlog.post.repository.PostCardQueries.FROM_CARD;
import static com.ownlog.post.repository.PostCardQueries.LATEST;
import static com.ownlog.post.repository.PostCardQueries.PUBLIC_CONDITION;

import com.ownlog.post.domain.Post;
import com.ownlog.universe.dto.GraphNodeRow;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** 기록 그래프 조회 전용 (쓰기 없음) */
public interface GraphRepository extends Repository<Post, Long> {

    /**
     * 별: 그 블로그의 발행 글, 최신순. 방문자는 글·카테고리가 공개인 것만(봉인 캡슐 포함, 서비스에서 잠금 처리),
     * 주인은 비공개도 모두. 봉인 캡슐을 빼면 공개 조건(ERD 4.1)과 같아요.
     */
    @Query("""
            select new com.ownlog.universe.dto.GraphNodeRow(p.id, p.title, t.slug, p.likeCount, p.publishedAt, p.sealedAt,
                                                            u.username, u.nickname)
              from Post p join p.category c left join p.topic t join p.blog b join b.user u
             where b.id = :blogId
               and p.status = com.ownlog.post.domain.PostStatus.PUBLISHED
               and (:owner = true or (p.visibility = com.ownlog.global.common.Visibility.PUBLIC
                                      and c.visibility = com.ownlog.global.common.Visibility.PUBLIC))
             order by p.publishedAt desc, p.id desc
            """)
    List<GraphNodeRow> findNodes(Long blogId, boolean owner, Pageable pageable);

    /** 14.3 서비스 전체 우주: 모든 블로그의 공개 글 (공개 조건 그대로, 봉인 캡슐 제외), 최신순 */
    @Query("select new com.ownlog.universe.dto.GraphNodeRow(p.id, p.title, t.slug, p.likeCount, p.publishedAt, p.sealedAt,"
            + " u.username, u.nickname)" + FROM_CARD + " where " + PUBLIC_CONDITION + LATEST)
    List<GraphNodeRow> findPublicNodes(Pageable pageable);

    /** 링크 선: 양 끝이 모두 별 목록에 있는 것 */
    @Query(value = """
            SELECT source_post_id AS "source", target_post_id AS "target"
              FROM post_links
             WHERE source_post_id IN (:ids) AND target_post_id IN (:ids)
            """, nativeQuery = true)
    List<LinkEdgeRow> findLinkEdges(Collection<Long> ids);

    /** 태그 선: 태그를 1개 이상 공유하는 글 쌍과 공유한 태그들, 많이 공유한 순으로 limit개 */
    @Query(value = """
            SELECT a.post_id AS "source", b.post_id AS "target", COUNT(*) AS "weight",
                   STRING_AGG(tg.name, ',' ORDER BY tg.name) AS "tags"
              FROM post_tags a
              JOIN post_tags b ON b.tag_id = a.tag_id AND a.post_id < b.post_id
              JOIN tags tg ON tg.id = a.tag_id
             WHERE a.post_id IN (:ids) AND b.post_id IN (:ids)
             GROUP BY a.post_id, b.post_id
             ORDER BY COUNT(*) DESC, a.post_id, b.post_id
             LIMIT :limit
            """, nativeQuery = true)
    List<TagEdgeRow> findTagEdges(Collection<Long> ids, int limit);

    /** 별에 마우스를 올렸을 때 보여줄 태그 */
    @Query(value = """
            SELECT pt.post_id AS "postId", tg.name AS "name"
              FROM post_tags pt JOIN tags tg ON tg.id = pt.tag_id
             WHERE pt.post_id IN (:ids)
             ORDER BY lower(tg.name)
            """, nativeQuery = true)
    List<PostTagRow> findTags(Collection<Long> ids);

    /**
     * 14.2 기록 잔디: 날짜(KST)별 쓴 글 수. 타임캡슐은 봉인한 날(실제로 쓴 날)로 세요 (AC-04).
     * 방문자는 글·카테고리가 공개인 것만 (봉인 캡슐은 "봉인했다"는 사실이 12.1로 이미 공개라 포함해요).
     */
    @Query(value = """
            SELECT CAST((COALESCE(p.sealed_at, p.published_at) AT TIME ZONE 'Asia/Seoul') AS date) AS "day",
                   COUNT(*) AS "count"
              FROM posts p JOIN categories c ON c.id = p.category_id
             WHERE p.blog_id = :blogId AND p.status = 'PUBLISHED'
               AND (:owner = true OR (p.visibility = 'PUBLIC' AND c.visibility = 'PUBLIC'))
               AND COALESCE(p.sealed_at, p.published_at) >= :from
             GROUP BY 1
             ORDER BY 1
            """, nativeQuery = true)
    List<ActivityRow> findActivity(Long blogId, boolean owner, Instant from);

    interface ActivityRow {
        LocalDate getDay();

        Long getCount();
    }

    interface LinkEdgeRow {
        Long getSource();

        Long getTarget();
    }

    interface TagEdgeRow {
        Long getSource();

        Long getTarget();

        Long getWeight();

        String getTags();
    }

    interface PostTagRow {
        Long getPostId();

        String getName();
    }
}
