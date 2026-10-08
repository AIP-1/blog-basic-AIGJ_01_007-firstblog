package com.ownlog.post.dto;

import com.ownlog.global.common.Visibility;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** API 7.1, 7.2 요청. PostRequest와 같지만 제목과 본문이 비어도 돼요 (D-03). 길이·형식 제한은 같아요 */
public record DraftRequest(
        @Size(max = 100, message = "제목은 100자 이하여야 해요.")
        String title,

        @Size(max = 100_000, message = "본문은 100,000자 이하여야 해요.")
        String content,

        @Size(max = 500, message = "썸네일 주소는 500자 이하여야 해요.")
        @Pattern(regexp = "^(/uploads/|https://).*", message = "썸네일 주소가 올바르지 않아요.")
        String thumbnailUrl,

        Long categoryId,
        Long topicId,

        List<@Pattern(regexp = PostRequest.TAG_REGEX, message = PostRequest.TAG_MESSAGE) String> tags,

        Visibility visibility
) {
    /** 공백뿐인 제목은 null로 저장해서 목록에 "제목 없음"으로 보이게 해요 */
    public String normalizedTitle() {
        return title == null || title.isBlank() ? null : title.strip();
    }
}
