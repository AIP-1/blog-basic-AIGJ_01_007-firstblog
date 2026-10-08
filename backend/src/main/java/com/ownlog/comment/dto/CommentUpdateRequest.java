package com.ownlog.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** API 8.3 요청 */
public record CommentUpdateRequest(
        @NotBlank(message = "댓글을 입력해 주세요.")
        @Size(max = 1000, message = "댓글은 1,000자 이하여야 해요.")
        String content
) {
}
