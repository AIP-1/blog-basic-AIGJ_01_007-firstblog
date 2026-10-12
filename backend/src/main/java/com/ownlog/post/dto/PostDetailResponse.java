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
        OffsetDateTime publishedAt, OffsetDateTime editedAt,
        TimeCapsuleInfo timeCapsule   // 일반 글은 null
) {
    /** 타임캡슐 정보 (TC-03). locked면 방문자에게 본문을 보여주지 않은 응답이에요 */
    public record TimeCapsuleInfo(OffsetDateTime sealedAt, OffsetDateTime openAt, boolean locked) {

        static TimeCapsuleInfo of(Post post, boolean locked) {
            return post.isTimeCapsule()
                    ? new TimeCapsuleInfo(KstTime.of(post.getSealedAt()), KstTime.of(post.getPublishedAt()), locked)
                    : null;
        }
    }

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
                KstTime.of(post.getPublishedAt()), KstTime.of(post.getEditedAt()),
                TimeCapsuleInfo.of(post, false));
    }

    /** 방문자가 봉인된 타임캡슐을 열었을 때: 제목과 작성자, 봉인·개봉 시각만 (TC-03) */
    public static PostDetailResponse locked(Post post) {
        Category category = post.getCategory();
        Blog blog = post.getBlog();
        return new PostDetailResponse(
                post.getId(), post.getTitle(), null, null, post.getVisibility(),
                new CategoryInfo(category.getId(), category.getName(), category.getVisibility()),
                null, List.of(),
                UserSummary.from(blog.getUser()),
                new BlogInfo(blog.getId(), blog.getName()),
                0, 0, false, false, false,
                KstTime.of(post.getPublishedAt()), null,
                TimeCapsuleInfo.of(post, true));
    }
}
