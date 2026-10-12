package com.ownlog.link.service;

import com.ownlog.global.error.BusinessException;
import com.ownlog.global.error.ErrorCode;
import com.ownlog.global.security.LoginUser;
import com.ownlog.global.util.KstTime;
import com.ownlog.global.util.LikePattern;
import com.ownlog.link.dto.LinkTarget;
import com.ownlog.link.dto.LinkedPostItem;
import com.ownlog.link.dto.LinkedPostRow;
import com.ownlog.link.repository.PostLinkRepository;
import com.ownlog.post.domain.Post;
import com.ownlog.post.repository.PostRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 글 연결 [[번호|글자]] (LK-01 ~ LK-05) */
@Service
@RequiredArgsConstructor
public class PostLinkService {

    private static final int BACKLINK_LIMIT = 50;
    private static final int SUGGESTION_LIMIT = 5;

    private final PostLinkRepository postLinkRepository;
    private final PostRepository postRepository;

    /** 발행·수정할 때 본문에서 링크를 다시 계산해서 저장해요 (LK-04). 글 저장과 같은 트랜잭션이에요 */
    @Transactional
    public void sync(Post post) {
        postLinkRepository.deleteBySource(post.getId());
        Set<Long> targetIds = PostLinkParser.parse(post.getContent());
        if (!targetIds.isEmpty()) {
            postLinkRepository.insertLinks(post.getId(), targetIds);
        }
    }

    @Transactional
    public void deleteAllOf(Long postId) {
        postLinkRepository.deleteAllOf(postId);
    }

    /**
     * 글 상세의 linkedPosts (LK-02): 본문에 있는 링크 대상마다 "이 사람이 볼 수 있나".
     * 볼 수 없거나 없는 글이면 제목도 주지 않아요 (비공개 글 제목이 새지 않게).
     */
    @Transactional(readOnly = true)
    public Map<Long, LinkTarget> linkTargets(Post post, LoginUser me) {
        Set<Long> ids = PostLinkParser.parse(post.getContent());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Long viewerId = me == null ? null : me.id();
        Map<Long, Post> found = postRepository.findAllForLinks(ids).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        Map<Long, LinkTarget> result = new LinkedHashMap<>();
        for (Long id : ids) {
            Post target = found.get(id);
            result.put(id, target != null && target.isViewableBy(viewerId)
                    ? new LinkTarget(true, target.getBlog().getUser().getUsername(), target.getTitle(),
                            target.getBlog().getUser().getNickname(), target.getSummary(), target.getThumbnailUrl(),
                            KstTime.of(target.getPublishedAt()))
                    : LinkTarget.INACCESSIBLE);
        }
        return result;
    }

    /** API 13.1 백링크: 대상 글을 볼 수 없으면 404, 볼 수 있으면 그 글을 언급한 글 중 볼 수 있는 것 */
    @Transactional(readOnly = true)
    public List<LinkedPostItem> backlinks(Long postId, LoginUser me) {
        Long viewerId = me == null ? null : me.id();
        postRepository.findById(postId)
                .filter(post -> post.isViewableBy(viewerId))
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        return postLinkRepository.findBacklinks(postId, viewerId == null ? -1L : viewerId, PageRequest.of(0, BACKLINK_LIMIT))
                .stream().map(LinkedPostItem::from).toList();
    }

    /** API 13.2 자동완성: 내 발행 글 최대 5개 + (검색어가 있으면) 다른 사람의 공개 글 최대 5개, 제목으로 찾아요 */
    @Transactional(readOnly = true)
    public List<LinkedPostItem> suggestions(LoginUser me, String q) {
        String keyword = q == null ? "" : q.strip();
        String pattern = LikePattern.contains(keyword);
        PageRequest limit = PageRequest.of(0, SUGGESTION_LIMIT);
        List<LinkedPostRow> mine = postRepository.findMyLinkCandidates(me.id(), pattern, limit);
        List<LinkedPostRow> others = keyword.isEmpty() ? List.of()
                : postRepository.findPublicLinkCandidates(me.id(), pattern, limit);
        return Stream.concat(mine.stream(), others.stream()).map(LinkedPostItem::from).toList();
    }
}
