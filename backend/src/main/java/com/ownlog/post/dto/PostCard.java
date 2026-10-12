package com.ownlog.post.dto;

import com.ownlog.global.common.Visibility;
import com.ownlog.global.util.KstTime;
import com.ownlog.topic.dto.TopicItem;
import com.ownlog.user.dto.UserSummary;
import java.time.OffsetDateTime;

/** API 명세서 2.2 PostCard */
public record PostCard(
        Long id, String title, String summary, String thumbnailUrl, int likeCount,
        OffsetDateTime publishedAt, UserSummary author, TopicItem topic,
        Visibility visibility, Visibility categoryVisibility,
        OffsetDateTime sealedAt) {   // 타임캡슐이면 봉인 시각, 일반 글은 null (TC-06 배지)

    public static PostCard from(PostCardRow row) {
        return new PostCard(row.id(), row.title(), row.summary(), row.thumbnailUrl(), row.likeCount(),
                KstTime.of(row.publishedAt()),
                UserSummary.of(row.username(), row.nickname(), row.profileImageUrl(), row.userStatus()),
                row.topicId() == null ? null : new TopicItem(row.topicId(), row.topicSlug(), row.topicName()),
                row.visibility(), row.categoryVisibility(), KstTime.of(row.sealedAt()));
    }
}
