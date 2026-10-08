package com.ownlog.post.dto;

import com.ownlog.blog.domain.Blog;
import com.ownlog.category.domain.Category;
import com.ownlog.global.common.Visibility;
import com.ownlog.global.util.KstTime;
import com.ownlog.post.domain.Post;
import com.ownlog.tag.domain.Tag;
import com.ownlog.topic.dto.TopicItem;
import com.ownlog.user.dto.UserSummary;
import java.time.OffsetDateTime;
import java.util.List;

/** API 명세서 6.5 응답 */
public record PostDetailResponse(
        Long id, String title, String content, String thumbnailUrl, Visibility visibility,
        CategoryInfo category, TopicItem topic, List<String> tags,
        UserSummary author, BlogInfo blog,
        int viewCount, int likeCount, boolean liked, boolean bookmarked, boolean isOwner,
        OffsetDateTime publishedAt, OffsetDateTime editedAt
) {
    public record CategoryInfo(Long id, String name, Visibility visibility) {
    }

    public record BlogInfo(Long id, String name) {
    }

    public static PostDetailResponse of(Post post, int viewCount, boolean liked,
                                        boolean bookmarked, boolean isOwner) {
        Category category = post.getCategory();
        Blog blog = post.getBlog();
        return new PostDetailResponse(
                post.getId(), post.getTitle(), post.getContent(), post.getThumbnailUrl(), post.getVisibility(),
                new CategoryInfo(category.getId(), category.getName(), category.getVisibility()),
                post.getTopic() == null ? null : TopicItem.from(post.getTopic()),
                post.getTags().stream()
                        .map(Tag::getName)
                        .sorted(String.CASE_INSENSITIVE_ORDER)   // 이름순 (P-05). DB 정렬 규칙에 기대지 않아요
                        .toList(),
                UserSummary.from(blog.getUser()),
                new BlogInfo(blog.getId(), blog.getName()),
                viewCount, post.getLikeCount(), liked, bookmarked, isOwner,
                KstTime.of(post.getPublishedAt()), KstTime.of(post.getEditedAt()));
    }
}
