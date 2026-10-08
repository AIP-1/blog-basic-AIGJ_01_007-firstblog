package com.ownlog.post.dto;

import com.ownlog.global.common.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** API 명세서 6.6, 6.7 요청 (발행 글은 제목과 본문이 필수예요) */
public record PostRequest(
        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(max = 100, message = "제목은 100자 이하여야 해요.")
        String title,

        @NotBlank(message = "본문을 입력해 주세요.")
        @Size(max = 100_000, message = "본문은 100,000자 이하여야 해요.")
        String content,

        @Size(max = 500, message = "썸네일 주소는 500자 이하여야 해요.")
        @Pattern(regexp = "^(/uploads/|https://).*", message = "썸네일 주소가 올바르지 않아요.")
        String thumbnailUrl,

        Long categoryId,   // null → "미분류"
        Long topicId,      // null → 주제 없음

        List<@Pattern(regexp = "^\\s*[가-힣a-zA-Z0-9_-]{1,20}\\s*$",
                message = "태그는 한글, 영문, 숫자, _, -만 쓸 수 있고 20자 이하예요.") String> tags,

        Visibility visibility   // null → PUBLIC
) {
}
