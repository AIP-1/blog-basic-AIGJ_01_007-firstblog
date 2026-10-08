package com.ownlog.topic.service;

import com.ownlog.global.error.BusinessException;
import com.ownlog.global.error.ErrorCode;
import com.ownlog.topic.domain.Topic;
import com.ownlog.topic.dto.TopicItem;
import com.ownlog.topic.repository.TopicRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TopicService {

    private final TopicRepository topicRepository;

    /** API 6.9 토픽 목록 (메인 칩, 글쓰기 선택 목록) */
    public List<TopicItem> getTopics() {
        return topicRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(TopicItem::from)
                .toList();
    }

    /** 글 작성·수정의 topicId 검증. null이면 "주제 없음"이라 null을 돌려줘요 (T-02) */
    public Topic getTopicOrNull(Long topicId) {
        if (topicId == null) {
            return null;
        }
        return topicRepository.findById(topicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOPIC));
    }

    /** 메인 목록의 ?topic=travel 검증 (API 6.1) */
    public Topic getBySlug(String slug) {
        return topicRepository.findBySlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOPIC));
    }
}
