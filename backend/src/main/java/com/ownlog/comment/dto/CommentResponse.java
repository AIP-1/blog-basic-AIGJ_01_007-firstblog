package com.ownlog.comment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ownlog.comment.domain.Comment;
import com.ownlog.global.util.KstTime;
import com.ownlog.user.dto.UserSummary;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * API 8.1 댓글 한 개. 답글에는 replies 필드가 없어요 (깊이 1단계).
 * 공통 규칙(빈 값도 null로 보냄)의 예외라서 replies에만 NON_NULL을 붙여요.
 */
public record CommentResponse(
        Long id, String content, UserSummary author,
        boolean isDeleted, boolean isEdited, OffsetDateTime createdAt,
        boolean canEdit, boolean canDelete, boolean canReport,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<CommentResponse> replies) {

    /** viewerId: 보는 사람 (방문자면 null), postOwnerId: 글의 블로그 주인 */
    public static CommentResponse of(Comment comment, Long viewerId, Long postOwnerId, List<CommentResponse> replies) {
        if (comment.isDeleted()) {   // 내용과 작성자를 숨기고, 아무 동작도 할 수 없어요
            return new CommentResponse(comment.getId(), null, null, true, false,
                    KstTime.of(comment.getCreatedAt()), false, false, false, replies);
        }
        boolean loggedIn = viewerId != null;
        boolean isWriter = loggedIn && comment.isWrittenBy(viewerId);
        return new CommentResponse(comment.getId(), comment.getContent(), UserSummary.from(comment.getUser()),
                false, comment.isEdited(), KstTime.of(comment.getCreatedAt()),
                isWriter,                                                // 수정: 작성자만
                isWriter || (loggedIn && viewerId.equals(postOwnerId)),  // 삭제: 작성자 또는 글 주인
                loggedIn && !isWriter,                                   // 신고: 내 댓글이 아니면
                replies);
    }
}
