package com.ownlog.reaction.dto;

import com.ownlog.global.common.Visibility;
import com.ownlog.global.util.KstTime;
import com.ownlog.post.dto.PostCard;
import com.ownlog.topic.dto.TopicItem;
import com.ownlog.user.dto.UserSummary;
import java.time.OffsetDateTime;

/** API 4.8 목록 항목: PostCard 필드 + bookmarkedAt (명세 예시처럼 평평한 구조) */
public record BookmarkedPostCard(
        Long id, String title, String summary, String thumbnailUrl, int likeCount,
        OffsetDateTime publishedAt, UserSummary author, TopicItem topic,
        Visibility visibility, Visibility categoryVisibility,
        OffsetDateTime bookmarkedAt) {

    public static BookmarkedPostCard from(BookmarkedPostRow row) {
        PostCard card = PostCard.from(row.toCardRow());
        return new BookmarkedPostCard(card.id(), card.title(), card.summary(), card.thumbnailUrl(),
                card.likeCount(), card.publishedAt(), card.author(), card.topic(),
                card.visibility(), card.categoryVisibility(), KstTime.of(row.bookmarkedAt()));
    }
}
