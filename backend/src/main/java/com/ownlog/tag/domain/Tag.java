package com.ownlog.tag.domain;

import com.ownlog.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 태그 (ERD 3.5). 서비스 전체가 함께 쓰는 사전이고, 대소문자를 구분하지 않아요 (LOWER(name) UNIQUE).
 * 새 태그는 TagRepository.insertIfAbsent()로만 만들어요 (동시 생성 대비).
 */
@Getter
@Entity
@Table(name = "tags")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tag extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String name;
}
