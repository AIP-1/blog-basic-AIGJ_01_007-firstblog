package com.ownlog.post.dto;

import com.ownlog.global.error.BusinessException;
import com.ownlog.global.error.ErrorCode;
import com.ownlog.global.error.ErrorResponse.FieldError;
import java.util.Arrays;
import java.util.List;

/** API 6.1 sort 파라미터. 소문자로 받아요 (latest, popular) */
public enum PostSort {
    LATEST, POPULAR;

    public static PostSort from(String value) {
        return Arrays.stream(values())
                .filter(sort -> sort.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        List.of(new FieldError("sort", "정렬은 latest 또는 popular만 쓸 수 있어요."))));
    }
}
