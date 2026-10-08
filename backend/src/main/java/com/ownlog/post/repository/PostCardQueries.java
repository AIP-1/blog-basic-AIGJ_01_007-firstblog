package com.ownlog.post.repository;

/** 글 카드 목록 쿼리의 공통 부분. 모든 목록 쿼리가 이 상수를 조합해서 써요. */
public final class PostCardQueries {

    /** 방문자가 볼 수 있는 글 (ERD 4.1). 이 조건을 고치면 모든 목록에 한꺼번에 적용돼요 */
    public static final String PUBLIC_CONDITION = """
             p.status = com.ownlog.post.domain.PostStatus.PUBLISHED
             and p.visibility = com.ownlog.global.common.Visibility.PUBLIC
             and c.visibility = com.ownlog.global.common.Visibility.PUBLIC
            """;

    /** 카드에 필요한 컬럼만 (본문 content는 읽지 않아요). 북마크 목록도 이 목록을 재사용해요 */
    public static final String CARD_FIELDS = """
                p.id, p.title, p.summary, p.thumbnailUrl, p.likeCount, p.publishedAt,
                u.username, u.nickname, u.profileImageUrl, u.status,
                t.id, t.slug, t.name, p.visibility, c.visibility
            """;

    public static final String SELECT_CARD = "select new com.ownlog.post.dto.PostCardRow(" + CARD_FIELDS + ")";

    public static final String FROM_CARD = """
             from Post p
             join p.blog b
             join b.user u
             join p.category c
             left join p.topic t
            """;

    public static final String COUNT = "select count(p)";

    public static final String LATEST = " order by p.publishedAt desc, p.id desc";

    private PostCardQueries() {
    }
}
