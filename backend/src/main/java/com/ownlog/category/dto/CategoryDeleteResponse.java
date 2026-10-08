package com.ownlog.category.dto;

/** API 명세서 5.7 응답: 몇 개의 글을 어느 카테고리("미분류")로 옮겼는지 */
public record CategoryDeleteResponse(int movedPostCount, Long movedToCategoryId) {
}
