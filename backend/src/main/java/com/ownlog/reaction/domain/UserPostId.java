package com.ownlog.reaction.domain;

import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 좋아요·북마크의 복합 키 (user_id, post_id). JPA 규칙상 equals/hashCode와 기본 생성자가 필요해요 */
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UserPostId implements Serializable {

    private Long userId;
    private Long postId;
}
