package com.ownlog.blog.domain;

import com.ownlog.global.entity.BaseTimeEntity;
import com.ownlog.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 블로그 (ERD 3.2). DB는 회원과 1:N이고, 1인 1블로그는 회원가입 서비스에서 제한해요. */
@Getter
@Entity
@Table(name = "blogs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Blog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 200)
    private String description;

    /** 가입할 때 만드는 기본 블로그. 이름은 "{닉네임}의 블로그" (U-02) */
    public static Blog createDefault(User owner) {
        Blog blog = new Blog();
        blog.user = owner;
        blog.name = owner.getNickname() + "의 블로그";
        return blog;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /** user.getId()는 LAZY 프록시여도 추가 쿼리 없이 id를 돌려줘요. */
    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }
}
