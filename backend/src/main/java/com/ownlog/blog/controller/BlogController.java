package com.ownlog.blog.controller;

import com.ownlog.blog.dto.BlogHomeResponse;
import com.ownlog.blog.dto.BlogResponse;
import com.ownlog.blog.dto.BlogUpdateRequest;
import com.ownlog.blog.service.BlogService;
import com.ownlog.global.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/blogs")
@RequiredArgsConstructor
public class BlogController {

    private final BlogService blogService;

    /** API 5.1 블로그 홈 정보 (🔓, 토큰이 있으면 isOwner 계산) */
    @GetMapping("/{username}")
    public BlogHomeResponse getBlogHome(@PathVariable String username, @AuthenticationPrincipal LoginUser me) {
        return blogService.getBlogHome(username, me);
    }

    /** API 5.2 블로그 이름·소개 수정 (👤) */
    @PutMapping("/{blogId}")
    public BlogResponse updateBlog(@PathVariable Long blogId, @AuthenticationPrincipal LoginUser me,
                                   @Valid @RequestBody BlogUpdateRequest request) {
        return blogService.updateBlog(blogId, me, request);
    }
}
