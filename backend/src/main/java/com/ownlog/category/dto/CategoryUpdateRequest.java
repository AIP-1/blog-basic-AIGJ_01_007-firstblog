package com.ownlog.category.dto;

import com.ownlog.global.common.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** API 명세서 5.6 요청. PUT이라 두 값 모두 보내요. */
public record CategoryUpdateRequest(
        @NotBlank(message = "카테고리 이름을 입력해 주세요.")
        @Size(max = 30, message = "카테고리 이름은 30자 이하여야 해요.")
        String name,

        @NotNull(message = "공개 범위를 선택해 주세요.")
        Visibility visibility
) {
}
