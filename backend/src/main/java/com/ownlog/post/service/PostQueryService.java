package com.ownlog.post.service;

import com.ownlog.blog.domain.Blog;
import com.ownlog.blog.service.BlogService;
import com.ownlog.global.common.PageCondition;
import com.ownlog.global.common.PageResponse;
import com.ownlog.global.error.BusinessException;
import com.ownlog.global.error.ErrorCode;
import com.ownlog.global.security.LoginUser;
import com.ownlog.global.util.KstTime;
import com.ownlog.global.util.LikePattern;
import com.ownlog.link.service.PostLinkService;
import com.ownlog.post.domain.Post;
import com.ownlog.post.dto.PostCard;
import com.ownlog.post.dto.PostCardRow;
import com.ownlog.post.dto.PostDetailResponse;
import com.ownlog.post.dto.PostSort;
import com.ownlog.post.dto.ReceivedCapsuleItem;
import com.ownlog.post.dto.TagPostsResponse;
import com.ownlog.post.dto.TimeCapsuleItem;
import com.ownlog.post.repository.PostRepository;
import com.ownlog.tag.domain.Tag;
import com.ownlog.tag.repository.TagRepository;
import com.ownlog.topic.service.TopicService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 글 읽기 전용 기능 (상세, 목록). 쓰기는 PostService에 있어요. */
@Service
@RequiredArgsConstructor
public class PostQueryService {

    private static final Duration POPULAR_PERIOD = Duration.ofDays(7);   // S-02 "최근 7일"
    private static final Duration OPENED_CAPSULE_PERIOD = Duration.ofDays(7);   // TC-06 "최근 열린"

    private final PostRepository postRepository;
    private final TagRepository tagRepository;
    private final BlogService blogService;
    private final TopicService topicService;
    private final PostLinkService postLinkService;

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
        Long viewerId = me == null ? null : me.id();
        if (!post.isViewableBy(viewerId)) {
            // 봉인만 안 풀린 공개 캡슐이나, 나에게 온 캡슐이면 잠금 카드만 (TC-03, TC-08). 조회수는 세지 않아요
            if (post.isSealed() && (post.isPublicExceptSeal() || post.isRecipient(viewerId))) {
                return PostDetailResponse.locked(post);
            }
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        int viewCount = post.getViewCount();
        if (!isOwner) {                                                     // 작성자 본인 조회는 세지 않아요 (C-04)
            postRepository.increaseViewCount(postId);
            viewCount += 1;   // 엔티티는 올리기 전 값이라 응답용으로 1을 더해요 (DB 값은 항상 정확)
        }

        boolean liked = me != null && postRepository.existsLike(me.id(), postId);
        boolean bookmarked = me != null && postRepository.existsBookmark(me.id(), postId);
        return PostDetailResponse.of(post, viewCount, liked, bookmarked, isOwner, postLinkService.linkTargets(post, me));
    }

    /**
     * 이 사람이 볼 수 있는 발행 글인가 (댓글, 좋아요, 북마크, 신고가 함께 써요).
     * 주인이면 비공개여도 OK, 아니면 공개 조건(ERD 4.1). 볼 수 없으면 존재를 숨기고 404.
     */
    @Transactional(readOnly = true)
    public Post getViewablePost(Long postId, LoginUser me) {
        return postRepository.findById(postId)
                .filter(post -> isViewable(post, me))
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    /** 볼 수 있나?만 참/거짓으로 (댓글 신고처럼 다른 에러 코드로 응답해야 할 때 써요) */
    public boolean isViewable(Post post, LoginUser me) {
        return post.isViewableBy(me == null ? null : me.id());
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
        Page<PostCardRow> rows = postRepository.search(LikePattern.contains(keyword), page.toPageable(Sort.unsorted()));
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

    /** API 12.1 곧 열리는 타임캡슐 (최대 20개). 주인에게는 비공개 캡슐도 보여줘요 */
    @Transactional(readOnly = true)
    public List<TimeCapsuleItem> getSealedCapsules(String username, LoginUser me) {
        Blog blog = blogService.getActiveBlog(username);
        return postRepository.findSealedCapsules(blog.getId(), blogService.isOwner(blog, me), PageRequest.of(0, 20))
                .stream().map(TimeCapsuleItem::from).toList();
    }

    /** API 12.3 나에게 온 타임캡슐: 아직 안 열린 것(개봉 빠른 순) 다음에 열린 것(최근 개봉 순) */
    @Transactional(readOnly = true)
    public List<ReceivedCapsuleItem> getReceivedCapsules(LoginUser me) {
        Instant now = Instant.now();
        return postRepository.findReceivedCapsules(me.id()).stream()
                .map(row -> ReceivedCapsuleItem.from(row, now))
                .sorted(Comparator.comparing(ReceivedCapsuleItem::opened)
                        .thenComparing((a, b) -> a.opened()
                                ? b.openAt().compareTo(a.openAt())
                                : a.openAt().compareTo(b.openAt())))
                .toList();
    }

    /** API 12.2 최근 7일 안에 열린 공개 타임캡슐 (최대 6개) */
    @Transactional(readOnly = true)
    public List<PostCard> getOpenedCapsules() {
        return postRepository.findOpenedCapsules(Instant.now().minus(OPENED_CAPSULE_PERIOD), PageRequest.of(0, 6))
                .stream().map(PostCard::from).toList();
    }

    /** API 6.4 블로그 글 목록. 주인에게는 비공개 글과 비공개 카테고리의 글도 보여줘요 */
    @Transactional(readOnly = true)
    public PageResponse<PostCard> getBlogPosts(String username, Long categoryId, LocalDate date, LoginUser me,
                                               PageCondition page) {
        Blog blog = blogService.getActiveBlog(username);
        Pageable pageable = page.toPageable(Sort.unsorted());
        // 날짜(KST)를 고르면 그날 0시 ~ 다음 날 0시, 안 고르면 전체 기간 (AC-03)
        Instant dayStart = date == null ? Instant.EPOCH : KstTime.startOfDay(date);
        Instant dayEnd = date == null ? Instant.parse("9999-12-31T00:00:00Z") : KstTime.startOfDay(date.plusDays(1));
        Page<PostCardRow> rows = blogService.isOwner(blog, me)
                ? postRepository.findBlogPostsForOwner(blog.getId(), categoryId, dayStart, dayEnd, pageable)
                : postRepository.findBlogPostsForVisitor(blog.getId(), categoryId, dayStart, dayEnd, pageable);
        return PageResponse.from(rows.map(PostCard::from));
    }
}
