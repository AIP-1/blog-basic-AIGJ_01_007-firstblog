package com.ownlog.universe.controller;

import com.ownlog.global.security.LoginUser;
import com.ownlog.universe.dto.ActivityResponse;
import com.ownlog.universe.dto.GraphResponse;
import com.ownlog.universe.service.ActivityService;
import com.ownlog.universe.service.GraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 기록 유니버스 화면용 API (14장) */
@RestController
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;
    private final ActivityService activityService;

    /** API 14.1 기록 그래프 (🔓, 주인이면 비공개 글 포함) */
    @GetMapping("/api/blogs/{username}/graph")
    public GraphResponse getGraph(@PathVariable String username, @AuthenticationPrincipal LoginUser me) {
        return graphService.getGraph(username, me);
    }

    /** API 14.2 기록 잔디 (🔓, 주인이면 비공개 글 포함) */
    @GetMapping("/api/blogs/{username}/activity")
    public ActivityResponse getActivity(@PathVariable String username, @AuthenticationPrincipal LoginUser me) {
        return activityService.getActivity(username, me);
    }
}
