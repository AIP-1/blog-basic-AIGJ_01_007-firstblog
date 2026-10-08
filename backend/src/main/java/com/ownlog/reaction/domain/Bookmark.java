package com.ownlog.reaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 북마크 (ERD 3.7). 복합 PK가 1인 1회를 보장해요. 저장·삭제는 리포지토리의 SQL로만 해요 (동시 요청 대비) */
@Getter
@Entity
@Table(name = "bookmarks")
@IdClass(UserPostId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bookmark {

    @Id
    private Long userId;

    @Id
    private Long postId;

    @Column(nullable = false)
    private Instant createdAt;
}
