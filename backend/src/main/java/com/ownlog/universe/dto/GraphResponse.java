package com.ownlog.universe.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

/** API 14.1 블로그 기록 우주, 14.3 서비스 전체 우주 (GR-01 ~ GR-07) */
public record GraphResponse(List<Node> nodes, List<Edge> edges, boolean truncated) {

    /**
     * 별. 잠긴 캡슐(locked)은 제목·쓴 시각·개봉 시각만 있고 토픽·좋아요·태그는 비워요.
     * writtenAt: 타임랩스에서 별이 태어나는 시각 (타임캡슐은 봉인한 시각).
     * username·nickname: 그 글의 블로그 (서비스 전체 우주에서 은하를 나눠요)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Node(Long id, String title, String topic, Integer likeCount, Integer linkCount,
                       List<String> tags, OffsetDateTime publishedAt, OffsetDateTime writtenAt,
                       boolean locked, OffsetDateTime openAt, String username, String nickname) {
    }

    /**
     * 선. type: LINK(source가 target을 [[링크]], 방향 있음) / RELATED(관련 있는 글, 방향 없음, GR-02).
     * RELATED만 weight(관련도 0~1, 소수 둘째 자리), tags(함께 붙은 드문 태그), keywords(함께 나온 단어)가 있어요.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Edge(Long source, Long target, String type, Double weight, List<String> tags, List<String> keywords) {

        public static Edge link(Long source, Long target) {
            return new Edge(source, target, "LINK", null, null, null);
        }
    }
}
