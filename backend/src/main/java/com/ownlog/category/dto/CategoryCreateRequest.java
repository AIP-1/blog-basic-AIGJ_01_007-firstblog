package com.ownlog.category.dto;

import com.ownlog.global.common.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** API 명세서 5.4 요청. visibility를 생략하면 PUBLIC이에요. */
public record CategoryCreateRequest(
        @NotBlank(message = "카테고리 이름을 입력해 주세요.")
        @Size(max = 30, message = "카테고리 이름은 30자 이하여야 해요.")
        String name,

        Visibility visibility
) {
}
