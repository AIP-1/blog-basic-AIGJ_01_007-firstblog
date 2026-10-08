package com.ownlog.topic.dto;

import com.ownlog.topic.domain.Topic;

/** API 명세서 2.4 TopicItem */
public record TopicItem(Long id, String slug, String name) {

    public static TopicItem from(Topic topic) {
        return new TopicItem(topic.getId(), topic.getSlug(), topic.getName());
    }
}
