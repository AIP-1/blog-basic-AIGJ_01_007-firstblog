package com.ownlog.global.common;

import java.util.List;
import org.springframework.data.domain.Page;

/** 목록 응답 (API 명세서 1.5). page는 1부터 세요. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber() + 1,
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
