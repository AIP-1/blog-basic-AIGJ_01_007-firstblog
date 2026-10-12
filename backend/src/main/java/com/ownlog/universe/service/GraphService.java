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
import java.util.Arrays;
import java.util.HashMap;
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
 * 별 수와 태그 선 수에 상한을 둬서 글이 많아도 화면과 쿼리가 무거워지지 않게 해요.
 * 방문자에게 잠긴 타임캡슐은 선이 없는 홀로 떠 있는 별이에요 (본문·태그로 내용을 짐작할 수 없게).
 */
@Service
@RequiredArgsConstructor
public class GraphService {

    static final int MAX_NODES = 300;
    static final int MAX_UNIVERSE_NODES = 500;
    static final int MAX_TAG_EDGES = 1000;
    static final int MAX_UNIVERSE_TAG_EDGES = 1500;

    private final GraphRepository graphRepository;
    private final BlogService blogService;

    /** API 14.1 블로그 하나의 우주: 주인은 비공개 글까지 */
    @Transactional(readOnly = true)
    public GraphResponse getGraph(String username, LoginUser me) {
        Blog blog = blogService.getActiveBlog(username);
        boolean owner = blogService.isOwner(blog, me);
        List<GraphNodeRow> rows = graphRepository.findNodes(blog.getId(), owner, PageRequest.of(0, MAX_NODES + 1));
        return build(rows, MAX_NODES, MAX_TAG_EDGES, owner);
    }

    /** API 14.3 서비스 전체 우주: 모든 블로그의 공개 글만 (봉인 캡슐은 공개 조건에서 이미 빠져요) */
    @Transactional(readOnly = true)
    public GraphResponse getUniverse() {
        List<GraphNodeRow> rows = graphRepository.findPublicNodes(PageRequest.of(0, MAX_UNIVERSE_NODES + 1));
        return build(rows, MAX_UNIVERSE_NODES, MAX_UNIVERSE_TAG_EDGES, false);
    }

    private GraphResponse build(List<GraphNodeRow> rows, int maxNodes, int maxTagEdges, boolean owner) {
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

        List<Edge> edges = new ArrayList<>();
        Map<Long, Integer> linkCount = new HashMap<>();
        if (!connectable.isEmpty()) {
            for (var link : graphRepository.findLinkEdges(connectable)) {
                edges.add(new Edge(link.getSource(), link.getTarget(), "LINK", null, null));
                linkCount.merge(link.getSource(), 1, Integer::sum);
                linkCount.merge(link.getTarget(), 1, Integer::sum);
            }
            for (var tag : graphRepository.findTagEdges(connectable, maxTagEdges)) {
                edges.add(new Edge(tag.getSource(), tag.getTarget(), "TAG", tag.getWeight().intValue(),
                        Arrays.asList(tag.getTags().split(","))));
            }
        }

        Map<Long, List<String>> tags = connectable.isEmpty() ? Map.of()
                : graphRepository.findTags(connectable).stream().collect(Collectors.groupingBy(
                        GraphRepository.PostTagRow::getPostId,
                        Collectors.mapping(GraphRepository.PostTagRow::getName, Collectors.toList())));

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
