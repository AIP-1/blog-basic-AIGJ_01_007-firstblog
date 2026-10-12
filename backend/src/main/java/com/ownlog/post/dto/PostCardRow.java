package com.ownlog.post.dto;

import com.ownlog.global.common.Visibility;
import com.ownlog.user.domain.UserStatus;
import java.time.Instant;

/** 카드 목록 쿼리 결과 한 줄 (평평한 구조). JPQL의 select new는 중첩 객체를 만들 수 없어서 거쳐 가요 */
public record PostCardRow(
        Long id, String title, String summary, String thumbnailUrl, int likeCount, Instant publishedAt, Instant sealedAt,
        String username, String nickname, String profileImageUrl, UserStatus userStatus,
        Long topicId, String topicSlug, String topicName,
        Visibility visibility, Visibility categoryVisibility) {
}
