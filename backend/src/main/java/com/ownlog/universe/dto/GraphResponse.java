package com.ownlog.universe.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

/** API 14.1 기록 그래프 (GR-01 ~ GR-04) */
public record GraphResponse(List<Node> nodes, List<Edge> edges, boolean truncated) {

    /** 별. 잠긴 캡슐(locked)은 제목·개봉 시각만 있고 토픽·좋아요·태그는 비워요 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Node(Long id, String title, String topic, Integer likeCount, Integer linkCount,
                       List<String> tags, OffsetDateTime publishedAt, boolean locked, OffsetDateTime openAt) {
    }

    /** 선. type: LINK(source가 target을 [[링크]]) / TAG(태그를 weight개 공유) */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Edge(Long source, Long target, String type, Integer weight, List<String> tags) {
    }
}
