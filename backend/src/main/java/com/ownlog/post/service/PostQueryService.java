package com.ownlog.post.service;

import com.ownlog.blog.domain.Blog;
import com.ownlog.blog.service.BlogService;
import com.ownlog.global.common.PageCondition;
import com.ownlog.global.common.PageResponse;
import com.ownlog.global.error.BusinessException;
import com.ownlog.global.error.ErrorCode;
import com.ownlog.global.security.LoginUser;
import com.ownlog.post.domain.Post;
import com.ownlog.post.dto.PostCard;
import com.ownlog.post.dto.PostCardRow;
import com.ownlog.post.dto.PostDetailResponse;
import com.ownlog.post.dto.PostSort;
import com.ownlog.post.dto.TagPostsResponse;
import com.ownlog.post.repository.PostRepository;
import com.ownlog.tag.domain.Tag;
import com.ownlog.tag.repository.TagRepository;
import com.ownlog.topic.service.TopicService;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 글 읽기 전용 기능 (상세, 목록). 쓰기는 PostService에 있어요. */
@Service
@RequiredArgsConstructor
public class PostQueryService {

    private static final Duration POPULAR_PERIOD = Duration.ofDays(7);   // S-02 "최근 7일"

    private final PostRepository postRepository;
    private final TagRepository tagRepository;
    private final BlogService blogService;
    private final TopicService topicService;

    /**
     * API 6.5 글 상세. 조회수를 올리기 때문에 읽기 전용이 아닌 트랜잭션이에요.
     * 볼 수 없는 글은 모두 404로 응답해서 "존재하지 않는 글"처럼 보이게 해요 (P-06).
     */
    @Transactional
    public PostDetailResponse getDetail(String username, Long postId, LoginUser me) {
        Blog blog = blogService.getActiveBlog(username);                    // 없으면 USER_NOT_FOUND

        Post post = postRepository.findDetailById(postId)
                .filter(p -> p.getBlog().getId().equals(blog.getId()))      // 주소의 아이디와 글 주인이 같아야 해요
                .filter(Post::isPublished)                                  // 임시저장 글은 상세로 볼 수 없어요
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        boolean isOwner = blogService.isOwner(blog, me);
        if (!isOwner && !post.isVisibleToPublic()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        int viewCount = post.getViewCount();
        if (!isOwner) {                                                     // 작성자 본인 조회는 세지 않아요 (C-04)
            postRepository.increaseViewCount(postId);
            viewCount += 1;   // 엔티티는 올리기 전 값이라 응답용으로 1을 더해요 (DB 값은 항상 정확)
        }

        boolean liked = me != null && postRepository.existsLike(me.id(), postId);
        boolean bookmarked = me != null && postRepository.existsBookmark(me.id(), postId);
        return PostDetailResponse.of(post, viewCount, liked, bookmarked, isOwner);
    }

    /** API 6.1 메인 최신·인기 (+ 토픽 필터) */
    @Transactional(readOnly = true)
    public PageResponse<PostCard> getMainPosts(String sort, String topicSlug, PageCondition page) {
        PostSort postSort = PostSort.from(sort);
        Long topicId = topicSlug == null ? null : topicService.getBySlug(topicSlug).getId();
        Pageable pageable = page.toPageable(Sort.unsorted());   // 정렬은 쿼리 안에 고정돼 있어요

        Page<PostCardRow> rows = switch (postSort) {
            case LATEST -> postRepository.findLatest(topicId, pageable);
            case POPULAR -> postRepository.findPopular(topicId, Instant.now().minus(POPULAR_PERIOD), pageable);
        };
        return PageResponse.from(rows.map(PostCard::from));
    }

    /** API 6.2 검색 (q는 컨트롤러에서 1~50자 검증). %, _, \는 글자 그대로 찾도록 이스케이프해요 */
    @Transactional(readOnly = true)
    public PageResponse<PostCard> search(String keyword, PageCondition page) {
        String escaped = keyword.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        Page<PostCardRow> rows = postRepository.search("%" + escaped + "%", page.toPageable(Sort.unsorted()));
        return PageResponse.from(rows.map(PostCard::from));
    }

    /** API 6.3 태그 모아보기. 없는 태그는 404가 아니라 빈 목록이에요 */
    @Transactional(readOnly = true)
    public TagPostsResponse getTagPosts(String tagName, PageCondition page) {
        Pageable pageable = page.toPageable(Sort.unsorted());
        Optional<Tag> tag = tagRepository.findByNameIgnoreCase(tagName);
        if (tag.isEmpty()) {
            return new TagPostsResponse(tagName, PageResponse.from(Page.<PostCard>empty(pageable)));
        }
        Page<PostCardRow> rows = postRepository.findByTag(tagName, pageable);
        return new TagPostsResponse(tag.get().getName(), PageResponse.from(rows.map(PostCard::from)));
    }

    /** API 6.4 블로그 글 목록. 주인에게는 비공개 글과 비공개 카테고리의 글도 보여줘요 */
    @Transactional(readOnly = true)
    public PageResponse<PostCard> getBlogPosts(String username, Long categoryId, LoginUser me, PageCondition page) {
        Blog blog = blogService.getActiveBlog(username);
        Pageable pageable = page.toPageable(Sort.unsorted());
        Page<PostCardRow> rows = blogService.isOwner(blog, me)
                ? postRepository.findBlogPostsForOwner(blog.getId(), categoryId, pageable)
                : postRepository.findBlogPostsForVisitor(blog.getId(), categoryId, pageable);
        return PageResponse.from(rows.map(PostCard::from));
    }
}
