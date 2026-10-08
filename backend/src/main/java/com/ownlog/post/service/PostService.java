package com.ownlog.post.service;

import com.ownlog.blog.domain.Blog;
import com.ownlog.blog.repository.BlogRepository;
import com.ownlog.category.domain.Category;
import com.ownlog.category.repository.CategoryRepository;
import com.ownlog.global.common.Visibility;
import com.ownlog.global.error.BusinessException;
import com.ownlog.global.error.ErrorCode;
import com.ownlog.global.security.LoginUser;
import com.ownlog.global.util.KstTime;
import com.ownlog.post.domain.Post;
import com.ownlog.post.dto.PostCreateResponse;
import com.ownlog.post.dto.PostRequest;
import com.ownlog.post.dto.PostUpdateResponse;
import com.ownlog.post.repository.PostRepository;
import com.ownlog.tag.service.TagService;
import com.ownlog.topic.service.TopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final BlogRepository blogRepository;
    private final CategoryRepository categoryRepository;
    private final TopicService topicService;
    private final TagService tagService;

    /** API 6.6 바로 발행. 블로그는 토큰의 회원으로 정해요 (요청에 blogId 없음) */
    @Transactional
    public PostCreateResponse create(LoginUser me, PostRequest request) {
        Blog blog = blogRepository.findFirstByUserIdOrderByIdAsc(me.id())
                .orElseThrow(() -> new IllegalStateException("블로그가 없는 회원: " + me.id()));
        Post post = postRepository.save(Post.publish(blog,
                resolveCategory(blog, request.categoryId()),
                topicService.getTopicOrNull(request.topicId()),
                request.title().strip(), request.content(), request.thumbnailUrl(),
                visibilityOrDefault(request.visibility()),
                tagService.resolveTags(request.tags())));
        return new PostCreateResponse(post.getId(), me.username());
    }

    /** API 6.7 발행 글 수정 */
    @Transactional
    public PostUpdateResponse update(Long postId, LoginUser me, PostRequest request) {
        Post post = getPostForOwner(postId, me);
        post.edit(resolveCategory(post.getBlog(), request.categoryId()),
                topicService.getTopicOrNull(request.topicId()),
                request.title().strip(), request.content(), request.thumbnailUrl(),
                visibilityOrDefault(request.visibility()),
                tagService.resolveTags(request.tags()));
        return new PostUpdateResponse(post.getId(), me.username(), KstTime.of(post.getEditedAt()));
    }

    /** API 6.8 삭제: 신고 → 댓글 → 좋아요 → 북마크 → (태그 연결은 JPA가) → 글 순서로 지워요 (ERD 5장) */
    @Transactional
    public void delete(Long postId, LoginUser me) {
        Post post = getPostForOwner(postId, me);
        postRepository.deleteReports(postId);
        postRepository.deleteComments(postId);
        postRepository.deleteLikes(postId);
        postRepository.deleteBookmarks(postId);
        postRepository.delete(post);   // post_tags 행은 Hibernate가 먼저 지워요
    }

    /**
     * 수정·삭제 대상 확인 (API 명세서 1.3)
     * - 없거나 임시저장 글 → 404 (임시저장 글은 7.x API로 다뤄요)
     * - 남의 글: 볼 수 있으면 403, 볼 수 없으면(비공개, 비공개 카테고리) 404
     */
    private Post getPostForOwner(Long postId, LoginUser me) {
        Post post = postRepository.findById(postId)
                .filter(Post::isPublished)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!post.isOwnedBy(me.id())) {
            throw new BusinessException(post.isVisibleToPublic() ? ErrorCode.FORBIDDEN : ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    /** null이면 "미분류". 내 블로그의 카테고리가 아니면 INVALID_CATEGORY */
    private Category resolveCategory(Blog blog, Long categoryId) {
        if (categoryId == null) {
            return categoryRepository.findByBlogIdAndIsDefaultTrue(blog.getId())
                    .orElseThrow(() -> new IllegalStateException("기본 카테고리가 없는 블로그: " + blog.getId()));
        }
        return categoryRepository.findById(categoryId)
                .filter(category -> category.getBlog().getId().equals(blog.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CATEGORY));
    }

    private Visibility visibilityOrDefault(Visibility visibility) {
        return visibility != null ? visibility : Visibility.PUBLIC;
    }
}
