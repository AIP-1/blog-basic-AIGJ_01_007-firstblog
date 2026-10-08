package com.ownlog.comment.controller;

import com.ownlog.comment.dto.CommentListResponse;
import com.ownlog.comment.dto.CommentRequest;
import com.ownlog.comment.dto.CommentResponse;
import com.ownlog.comment.dto.CommentUpdateRequest;
import com.ownlog.comment.service.CommentService;
import com.ownlog.global.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /** API 8.1 댓글 목록 (🔓) */
    @GetMapping("/api/posts/{postId}/comments")
    public CommentListResponse getComments(@PathVariable Long postId, @AuthenticationPrincipal LoginUser me) {
        return commentService.getComments(postId, me);
    }

    /** API 8.2 댓글·답글 작성 (🔒) → 201 */
    @PostMapping("/api/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> create(@PathVariable Long postId, @AuthenticationPrincipal LoginUser me,
                                                  @Valid @RequestBody CommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commentService.create(postId, me, request));
    }

    /** API 8.3 댓글 수정 (👤 작성자) */
    @PatchMapping("/api/comments/{commentId}")
    public CommentResponse update(@PathVariable Long commentId, @AuthenticationPrincipal LoginUser me,
                                  @Valid @RequestBody CommentUpdateRequest request) {
        return commentService.update(commentId, me, request);
    }

    /** API 8.4 댓글 삭제 (👤 작성자, 글 주인) → 204 */
    @DeleteMapping("/api/comments/{commentId}")
    public ResponseEntity<Void> delete(@PathVariable Long commentId, @AuthenticationPrincipal LoginUser me) {
        commentService.delete(commentId, me);
        return ResponseEntity.noContent().build();
    }
}
