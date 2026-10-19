package com.ownlog.post.service;

import com.ownlog.blog.domain.Blog;
import com.ownlog.blog.service.BlogService;
import com.ownlog.category.service.CategoryService;
import com.ownlog.global.common.Visibility;
import com.ownlog.global.error.BusinessException;
import com.ownlog.global.error.ErrorCode;
import com.ownlog.global.security.LoginUser;
import com.ownlog.global.storage.UnusedImageCleaner;
import com.ownlog.global.util.KstTime;
import com.ownlog.link.service.PostLinkService;
import com.ownlog.post.domain.Post;
import com.ownlog.post.dto.PostCreateResponse;
import com.ownlog.post.dto.PostRequest;
import com.ownlog.post.dto.PostUpdateResponse;
import com.ownlog.post.repository.PostRepository;
import com.ownlog.tag.service.TagService;
import com.ownlog.user.domain.User;
import java.time.Instant;
import java.util.Set;
import com.ownlog.topic.service.TopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final BlogService blogService;
    private final CategoryService categoryService;
    private final TopicService topicService;
    private final TagService tagService;
    private final PostLinkService postLinkService;
    private final CapsuleRecipientResolver recipientResolver;
    private final UnusedImageCleaner unusedImageCleaner;

    /** API 6.6 바로 발행. 블로그는 토큰의 회원으로 정해요 (요청에 blogId 없음) */
    @Transactional
    public PostCreateResponse create(LoginUser me, PostRequest request) {
        Blog blog = blogService.getMyBlog(me.id());
        Instant openAt = TimeCapsulePolicy.openAtOrNull(request.openAt());
        User recipient = recipientResolver.resolve(request.recipientUsername(), openAt, me);
        Post post = postRepository.save(Post.publish(blog,
                categoryService.getCategoryForPost(blog, request.categoryId()),
                topicService.getTopicOrNull(request.topicId()),
                request.title().strip(), request.content(), request.thumbnailUrl(),
                Visibility.orPublic(request.visibility()),
                tagService.resolveTags(request.tags()),
                openAt, recipient));
        postLinkService.sync(post);
        return new PostCreateResponse(post.getId(), me.username());
    }

    /** API 6.7 발행 글 수정 */
    @Transactional
    public PostUpdateResponse update(Long postId, LoginUser me, PostRequest request) {
        Post post = getPostForOwner(postId, me);
        if (post.isSealed()) {
            throw new BusinessException(ErrorCode.TIME_CAPSULE_SEALED);   // 봉인 중에는 수정 불가 (TC-04)
        }
        Set<String> before = UnusedImageCleaner.imagesOf(post.getThumbnailUrl(), post.getContent());
        post.edit(categoryService.getCategoryForPost(post.getBlog(), request.categoryId()),
                topicService.getTopicOrNull(request.topicId()),
                request.title().strip(), request.content(), request.thumbnailUrl(),
                Visibility.orPublic(request.visibility()),
                tagService.resolveTags(request.tags()));
        postLinkService.sync(post);
        // 수정하면서 빠진 이미지만 정리해요 (그대로 쓰는 이미지는 지우지 않아요)
        before.removeAll(UnusedImageCleaner.imagesOf(post.getThumbnailUrl(), post.getContent()));
        unusedImageCleaner.deleteIfUnused(before);
        return new PostUpdateResponse(post.getId(), me.username(), KstTime.of(post.getEditedAt()));
    }

    /** API 6.8 삭제: 신고 → 댓글 → 좋아요 → 북마크 → (태그 연결은 JPA가) → 글 순서로 지워요 (ERD 5장) */
    @Transactional
    public void delete(Long postId, LoginUser me) {
        Post post = getPostForOwner(postId, me);
        Set<String> images = UnusedImageCleaner.imagesOf(post.getThumbnailUrl(), post.getContent());
        postRepository.deleteReports(postId);
        postRepository.deleteComments(postId);
        postRepository.deleteLikes(postId);
        postRepository.deleteBookmarks(postId);
        postLinkService.deleteAllOf(postId);
        postRepository.delete(post);   // post_tags 행은 Hibernate가 먼저 지워요
        unusedImageCleaner.deleteIfUnused(images);   // 다른 곳에서 쓰지 않는 이미지만, 커밋 후에 파일 삭제
    }

    /**
     * 수정·삭제 대상 확인 (API 명세서 1.3)
     * - 없거나 임시저장 글 → 404 (임시저장 글은 7.x API로 다뤄요)
     * - 남의 글: 볼 수 있으면(공개 글, 나에게 온 열린 캡슐) 403, 볼 수 없으면(비공개, 비공개 카테고리) 404
     */
    private Post getPostForOwner(Long postId, LoginUser me) {
        Post post = postRepository.findById(postId)
                .filter(Post::isPublished)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!post.isOwnedBy(me.id())) {
            throw new BusinessException(post.isViewableBy(me.id()) ? ErrorCode.FORBIDDEN : ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }
}
