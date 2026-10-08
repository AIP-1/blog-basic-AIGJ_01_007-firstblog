package com.ownlog.post.dto;

import com.ownlog.global.common.PageResponse;

/** API 명세서 6.3 응답: 화면 제목에 쓸 대표 표기 + 글 목록 */
public record TagPostsResponse(String tag, PageResponse<PostCard> posts) {
}
