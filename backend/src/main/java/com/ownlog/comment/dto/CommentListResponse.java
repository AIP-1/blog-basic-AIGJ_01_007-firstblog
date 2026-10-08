package com.ownlog.comment.dto;

import java.util.List;

/** API 8.1 응답. totalCount에는 "삭제된 댓글입니다"를 세지 않아요 */
public record CommentListResponse(long totalCount, List<CommentResponse> comments) {
}
