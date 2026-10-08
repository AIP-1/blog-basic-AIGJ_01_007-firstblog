package com.ownlog.reaction.dto;

import com.ownlog.global.common.Visibility;
import com.ownlog.post.dto.PostCardRow;
import com.ownlog.user.domain.UserStatus;
import java.time.Instant;

/** 북마크 목록 쿼리 결과 한 줄: 카드 컬럼(PostCardQueries.CARD_FIELDS) + 북마크한 시각 */
public record BookmarkedPostRow(
        Long id, String title, String summary, String thumbnailUrl, int likeCount, Instant publishedAt,
        String username, String nickname, String profileImageUrl, UserStatus userStatus,
        Long topicId, String topicSlug, String topicName,
        Visibility visibility, Visibility categoryVisibility,
        Instant bookmarkedAt) {

    public PostCardRow toCardRow() {
        return new PostCardRow(id, title, summary, thumbnailUrl, likeCount, publishedAt,
                username, nickname, profileImageUrl, userStatus, topicId, topicSlug, topicName,
                visibility, categoryVisibility);
    }
}
