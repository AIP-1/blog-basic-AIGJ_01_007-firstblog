package com.ownlog.global.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 목록 API의 ?page=1&size=12 (API 명세서 1.5).
 * API는 1부터, Spring Data는 0부터 세기 때문에 toPageable()에서 1을 빼요.
 * 정렬은 클라이언트가 고르지 못하게 서비스에서 정해서 넘겨요.
 */
public record PageCondition(
        @Min(value = 1, message = "페이지는 1 이상이어야 해요.")
        Integer page,

        @Min(value = 1, message = "한 페이지 크기는 1 이상이어야 해요.")
        @Max(value = 50, message = "한 페이지 크기는 50 이하여야 해요.")
        Integer size
) {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 12;

    public PageCondition {
        if (page == null) page = DEFAULT_PAGE;
        if (size == null) size = DEFAULT_SIZE;
    }

    public Pageable toPageable(Sort sort) {
        return PageRequest.of(page - 1, size, sort);
    }
}
