package com.ownlog.universe.service;

import com.ownlog.blog.domain.Blog;
import com.ownlog.blog.service.BlogService;
import com.ownlog.global.security.LoginUser;
import com.ownlog.global.util.KstTime;
import com.ownlog.universe.dto.GraphNodeRow;
import com.ownlog.universe.dto.GraphResponse;
import com.ownlog.universe.dto.GraphResponse.Edge;
import com.ownlog.universe.dto.GraphResponse.Node;
import com.ownlog.universe.repository.GraphRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기록 우주 (GR-01 ~ GR-07). 블로그 하나의 우주(14.1)와 서비스 전체 우주(14.3)가 같은 방식으로 별과 선을 만들어요.
 * 별 수와 관련 선 수에 상한을 둬서 글이 많아도 화면과 쿼리가 무거워지지 않게 해요.
 * 방문자에게 잠긴 타임캡슐은 선이 없는 홀로 떠 있는 별이에요 (본문·태그로 내용을 짐작할 수 없게).
 *
 * <p>선은 두 가지예요. LINK는 글쓴이가 직접 건 [[링크]]라 그대로 보여주고,
 * RELATED는 제목·요약·태그의 TF-IDF 유사도로 "정말 관련 있는" 쌍만 골라요 (GR-02, {@link Relatedness}).
 * 예전에는 태그를 하나라도 같이 쓰면 모두 이어서, "여행"처럼 흔한 태그 하나로 선이 거미줄처럼 생겼어요.
 */
@Service
@RequiredArgsConstructor
public class GraphService {

    static final int MAX_NODES = 300;
    static final int MAX_UNIVERSE_NODES = 500;
    static final int MAX_RELATED_EDGES = 1000;
    static final int MAX_UNIVERSE_RELATED_EDGES = 1500;
    /**
     * 관련도가 이보다 낮으면 선을 긋지 않아요. 태그만 보면 "무게로 따져 태그의 1/3 이상이 겹친다"는 뜻이에요
     * (태그 2개 중 1개가 같고 무게가 같으면 0.33). 같은 태그가 다른 태그보다 흔하면 1/3보다 낮아져서 빠져요.
     * 시연 데이터 측정값과 조정 방법은 docs/guide/기록-우주-연관도.md "기준값 고르기"에 있어요.
     */
    static final double MIN_RELATED_SCORE = 0.3;
    /** 별마다 가장 가까운 3개까지만: 글이 많아도 별 하나에 선이 몰리지 않게 해요 */
    static final int RELATED_TOP_K = 3;

    private final GraphRepository graphRepository;
    private final BlogService blogService;

    /** API 14.1 블로그 하나의 우주: 주인은 비공개 글까지 */
    @Transactional(readOnly = true)
    public GraphResponse getGraph(String username, LoginUser me) {
        Blog blog = blogService.getActiveBlog(username);
        boolean owner = blogService.isOwner(blog, me);
        List<GraphNodeRow> rows = graphRepository.findNodes(blog.getId(), owner, PageRequest.of(0, MAX_NODES + 1));
        return build(rows, MAX_NODES, MAX_RELATED_EDGES, owner);
    }

    /** API 14.3 서비스 전체 우주: 모든 블로그의 공개 글만 (봉인 캡슐은 공개 조건에서 이미 빠져요) */
    @Transactional(readOnly = true)
    public GraphResponse getUniverse() {
        List<GraphNodeRow> rows = graphRepository.findPublicNodes(PageRequest.of(0, MAX_UNIVERSE_NODES + 1));
        return build(rows, MAX_UNIVERSE_NODES, MAX_UNIVERSE_RELATED_EDGES, false);
    }

    private GraphResponse build(List<GraphNodeRow> rows, int maxNodes, int maxRelatedEdges, boolean owner) {
        boolean truncated = rows.size() > maxNodes;
        if (truncated) {
            rows = rows.subList(0, maxNodes);
        }
        if (rows.isEmpty()) {
            return new GraphResponse(List.of(), List.of(), false);
        }
        Instant now = Instant.now();

        // 선을 이을 수 있는 별: 방문자에게 잠긴 캡슐은 빼요 (주인은 모두)
        Set<Long> connectable = rows.stream()
                .filter(row -> owner || !row.isSealed(now))
                .map(GraphNodeRow::id)
                .collect(Collectors.toSet());

        // 태그는 선을 이을 수 있는 별 것만 읽어요: 잠긴 캡슐의 태그는 응답에도, 관련도 계산에도 들어가지 않아요
        Map<Long, List<String>> tags = connectable.isEmpty() ? Map.of()
                : graphRepository.findTags(connectable).stream().collect(Collectors.groupingBy(
                        GraphRepository.PostTagRow::getPostId,
                        Collectors.mapping(GraphRepository.PostTagRow::getName, Collectors.toList())));

        List<Edge> edges = new ArrayList<>();
        Map<Long, Integer> linkCount = new HashMap<>();
        if (!connectable.isEmpty()) {
            Set<Relatedness.Key> linked = new HashSet<>();
            for (var link : graphRepository.findLinkEdges(connectable)) {
                edges.add(Edge.link(link.getSource(), link.getTarget()));
                linked.add(Relatedness.Key.of(link.getSource(), link.getTarget()));
                linkCount.merge(link.getSource(), 1, Integer::sum);
                linkCount.merge(link.getTarget(), 1, Integer::sum);
            }
            // 관련 있는 글: 볼 수 있는(connectable) 글의 제목·요약·태그만 넣어요. [[링크]]로 이미 이어진 쌍은 선이 겹치니 빼요
            List<Relatedness.Doc> docs = rows.stream()
                    .filter(row -> connectable.contains(row.id()))
                    .map(row -> new Relatedness.Doc(row.id(), row.title(), row.summary(),
                            tags.getOrDefault(row.id(), List.of())))
                    .toList();
            var options = new Relatedness.Options(MIN_RELATED_SCORE, RELATED_TOP_K, maxRelatedEdges);
            for (var pair : Relatedness.compute(docs, linked, options)) {
                edges.add(new Edge(pair.source(), pair.target(), "RELATED", pair.score(), pair.tags(), pair.keywords()));
            }
        }

        List<Node> nodes = rows.stream().map(row -> {
            boolean sealed = row.isSealed(now);
            boolean hideDetails = sealed && !owner;
            return new Node(row.id(), row.title(),
                    hideDetails ? null : row.topicSlug(),
                    hideDetails ? null : row.likeCount(),
                    hideDetails ? null : linkCount.getOrDefault(row.id(), 0),
                    hideDetails ? null : tags.getOrDefault(row.id(), List.of()),
                    hideDetails ? null : KstTime.of(row.publishedAt()),
                    KstTime.of(row.writtenAt()),
                    sealed,
                    sealed ? KstTime.of(row.publishedAt()) : null,
                    row.username(), row.nickname());
        }).toList();

        return new GraphResponse(nodes, edges, truncated);
    }
}
