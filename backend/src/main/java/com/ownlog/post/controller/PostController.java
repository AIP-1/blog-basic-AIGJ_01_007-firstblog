package com.ownlog.post.controller;

import com.ownlog.global.security.LoginUser;
import com.ownlog.post.dto.PostCreateResponse;
import com.ownlog.post.dto.PostRequest;
import com.ownlog.post.dto.PostUpdateResponse;
import com.ownlog.post.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /** API 6.6 글 바로 발행 (🔒) → 201 */
    @PostMapping
    public ResponseEntity<PostCreateResponse> create(@AuthenticationPrincipal LoginUser me,
                                                     @Valid @RequestBody PostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.create(me, request));
    }

    /** API 6.7 발행 글 수정 (👤) */
    @PutMapping("/{postId}")
    public PostUpdateResponse update(@PathVariable Long postId, @AuthenticationPrincipal LoginUser me,
                                     @Valid @RequestBody PostRequest request) {
        return postService.update(postId, me, request);
    }

    /** API 6.8 글 삭제 (👤) → 204 */
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> delete(@PathVariable Long postId, @AuthenticationPrincipal LoginUser me) {
        postService.delete(postId, me);
        return ResponseEntity.noContent().build();
    }
}
