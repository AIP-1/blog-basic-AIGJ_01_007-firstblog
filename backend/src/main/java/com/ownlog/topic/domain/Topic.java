package com.ownlog.topic.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * 토픽 (ERD 3.9). 서비스가 정한 고정 목록이라 Flyway로만 넣고 바꿔요.
 * @Immutable: Hibernate가 이 엔티티의 변경을 감지하지 않아요 (실수로 바꿔도 UPDATE가 나가지 않아요).
 */
@Getter
@Entity
@Immutable
@Table(name = "topics")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Topic {

    @Id
    private Long id;

    @Column(nullable = false, length = 30)
    private String slug;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(nullable = false)
    private int displayOrder;
}
