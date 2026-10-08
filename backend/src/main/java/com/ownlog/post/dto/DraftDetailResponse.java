package com.ownlog.post.dto;

import com.ownlog.category.domain.Category;
import com.ownlog.global.common.Visibility;
import com.ownlog.global.util.KstTime;
import com.ownlog.post.domain.Post;
import com.ownlog.post.dto.PostDetailResponse.CategoryInfo;
import com.ownlog.tag.domain.Tag;
import com.ownlog.topic.dto.TopicItem;
import java.time.OffsetDateTime;
import java.util.List;

/** API 7.4 응답 (이어 쓰기) */
public record DraftDetailResponse(
        Long id, String title, String content, String thumbnailUrl,
        CategoryInfo category, TopicItem topic, List<String> tags,
        Visibility visibility, OffsetDateTime updatedAt) {

    public static DraftDetailResponse from(Post draft) {
        Category category = draft.getCategory();
        return new DraftDetailResponse(draft.getId(), draft.getTitle(), draft.getContent(), draft.getThumbnailUrl(),
                new CategoryInfo(category.getId(), category.getName(), category.getVisibility()),
                draft.getTopic() == null ? null : TopicItem.from(draft.getTopic()),
                draft.getTags().stream().map(Tag::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList(),
                draft.getVisibility(), KstTime.of(draft.getUpdatedAt()));
    }
}
