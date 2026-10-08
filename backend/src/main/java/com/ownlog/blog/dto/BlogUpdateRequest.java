package com.ownlog.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** API 명세서 5.2 요청. PUT이라 폼 전체를 보내고, description이 null이면 지워요. */
public record BlogUpdateRequest(
        @NotBlank(message = "블로그 이름을 입력해 주세요.")
        @Size(max = 50, message = "블로그 이름은 50자 이하여야 해요.")
        String name,

        @Size(max = 200, message = "블로그 소개는 200자 이하여야 해요.")
        String description
) {
}
