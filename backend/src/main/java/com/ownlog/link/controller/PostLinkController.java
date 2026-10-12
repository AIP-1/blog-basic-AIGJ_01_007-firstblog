package com.ownlog.link.controller;

import com.ownlog.global.security.LoginUser;
import com.ownlog.link.dto.LinkedPostItem;
import com.ownlog.link.service.PostLinkService;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 글 연결 (API 13장). 링크 저장은 글 발행·수정 API가 본문을 보고 알아서 해요 */
@RestController
@RequiredArgsConstructor
public class PostLinkController {

    private final PostLinkService postLinkService;

    /** API 13.1 이 글을 언급한 글 (🔓) */
    @GetMapping("/api/posts/{postId}/backlinks")
    public List<LinkedPostItem> getBacklinks(@PathVariable Long postId, @AuthenticationPrincipal LoginUser me) {
        return postLinkService.backlinks(postId, me);
    }

    /** API 13.2 [[ 자동완성 (🔒) */
    @GetMapping("/api/posts/link-suggestions")
    public List<LinkedPostItem> getSuggestions(@AuthenticationPrincipal LoginUser me,
                                               @RequestParam(defaultValue = "")
                                               @Size(max = 50, message = "검색어는 50자 이하여야 해요.") String q) {
        return postLinkService.suggestions(me, q);
    }
}
