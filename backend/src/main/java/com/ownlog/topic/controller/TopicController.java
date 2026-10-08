package com.ownlog.topic.controller;

import com.ownlog.topic.dto.TopicItem;
import com.ownlog.topic.service.TopicService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;

    /** API 6.9 토픽 목록 (🔓) */
    @GetMapping("/api/topics")
    public List<TopicItem> getTopics() {
        return topicService.getTopics();
    }
}
