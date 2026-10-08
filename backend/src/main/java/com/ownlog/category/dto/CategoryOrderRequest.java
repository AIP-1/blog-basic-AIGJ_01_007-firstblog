package com.ownlog.category.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** API 명세서 5.5 요청: 바뀐 순서대로 블로그의 모든 카테고리 id */
public record CategoryOrderRequest(
        @NotEmpty(message = "카테고리 순서를 보내 주세요.")
        List<@NotNull Long> categoryIds
) {
}
