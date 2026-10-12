package com.ownlog.link.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 글 → 글 연결 (V3). 저장·삭제는 리포지토리의 SQL로만 해요. JPQL 조회(백링크, 그래프)에 쓰려고 엔티티로 둬요 */
@Getter
@Entity
@Table(name = "post_links")
@IdClass(PostLinkId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostLink {

    @Id
    private Long sourcePostId;

    @Id
    private Long targetPostId;

    @Column(nullable = false)
    private Instant createdAt;
}
