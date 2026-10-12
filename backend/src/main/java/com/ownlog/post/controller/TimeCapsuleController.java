package com.ownlog.post.controller;

import com.ownlog.global.security.LoginUser;
import com.ownlog.post.dto.PostCard;
import com.ownlog.post.dto.TimeCapsuleItem;
import com.ownlog.post.service.PostQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 타임캡슐 목록 (API 12장). 발행은 기존 글 발행 API에 openAt을 넣어서 해요 */
@RestController
@RequiredArgsConstructor
public class TimeCapsuleController {

    private final PostQueryService postQueryService;

    /** API 12.1 곧 열리는 타임캡슐 (🔓) */
    @GetMapping("/api/blogs/{username}/time-capsules")
    public List<TimeCapsuleItem> getSealedCapsules(@PathVariable String username,
                                                   @AuthenticationPrincipal LoginUser me) {
        return postQueryService.getSealedCapsules(username, me);
    }

    /** API 12.2 최근 열린 타임캡슐 (🔓) */
    @GetMapping("/api/time-capsules/opened")
    public List<PostCard> getOpenedCapsules() {
        return postQueryService.getOpenedCapsules();
    }
}
