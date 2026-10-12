package com.ownlog.post.service;

import com.ownlog.blog.domain.Blog;
import com.ownlog.blog.service.BlogService;
import com.ownlog.category.service.CategoryService;
import com.ownlog.global.common.PageCondition;
import com.ownlog.global.common.PageResponse;
import com.ownlog.global.common.Visibility;
import com.ownlog.global.error.BusinessException;
import com.ownlog.global.error.ErrorCode;
import com.ownlog.global.security.LoginUser;
import com.ownlog.link.service.PostLinkService;
import com.ownlog.post.domain.Post;
import com.ownlog.post.domain.PostStatus;
import com.ownlog.post.dto.DraftDetailResponse;
import com.ownlog.post.dto.DraftItem;
import com.ownlog.post.dto.DraftRequest;
import com.ownlog.post.dto.DraftSaveResponse;
import com.ownlog.post.dto.PostCreateResponse;
import com.ownlog.post.dto.PostRequest;
import com.ownlog.post.repository.PostRepository;
import com.ownlog.tag.service.TagService;
import com.ownlog.topic.service.TopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 임시저장 (API 7.1 ~ 7.6). 임시저장 글은 본인에게만 존재해서, 남이 접근하면 항상 404예요. */
@Service
@RequiredArgsConstructor
public class DraftService {

    private final PostRepository postRepository;
    private final BlogService blogService;
    private final CategoryService categoryService;
    private final TopicService topicService;
    private final TagService tagService;
    private final PostLinkService postLinkService;

    /** API 7.1 처음 임시저장 → 받은 id로 다음부터는 7.2를 호출해요 */
    @Transactional
    public DraftSaveResponse create(LoginUser me, DraftRequest request) {
        Blog blog = blogService.getMyBlog(me.id());
        Post draft = postRepository.save(Post.createDraft(blog,
                categoryService.getCategoryForPost(blog, request.categoryId()),
                topicService.getTopicOrNull(request.topicId()),
                request.normalizedTitle(), request.content(), request.thumbnailUrl(),
                Visibility.orPublic(request.visibility()),
                tagService.resolveTags(request.tags())));
        return DraftSaveResponse.from(draft);
    }

    /** API 7.2 다시 임시저장 (덮어쓰기) */
    @Transactional
    public DraftSaveResponse save(Long postId, LoginUser me, DraftRequest request) {
        Post draft = getMyDraft(postId, me);
        draft.saveDraft(categoryService.getCategoryForPost(draft.getBlog(), request.categoryId()),
                topicService.getTopicOrNull(request.topicId()),
                request.normalizedTitle(), request.content(), request.thumbnailUrl(),
                Visibility.orPublic(request.visibility()),
                tagService.resolveTags(request.tags()));
        postRepository.flush();   // 지금 UPDATE해야 updatedAt이 갱신돼서 "임시저장됨 (HH:mm)"이 맞아요
        return DraftSaveResponse.from(draft);
    }

    /** API 7.3 임시저장 목록: 최근 저장 순 (D-04) */
    @Transactional(readOnly = true)
    public PageResponse<DraftItem> getDrafts(LoginUser me, PageCondition page) {
        Blog blog = blogService.getMyBlog(me.id());
        return PageResponse.from(postRepository.findDrafts(blog.getId(), page.toPageable(Sort.unsorted()))
                .map(DraftItem::from));
    }

    /** API 7.4 불러오기 (이어 쓰기) */
    @Transactional(readOnly = true)
    public DraftDetailResponse getDraft(Long postId, LoginUser me) {
        return DraftDetailResponse.from(getMyDraft(postId, me));
    }

    /** API 7.5 삭제. 임시저장 글에는 댓글·좋아요가 달릴 수 없어서 글만 지우면 돼요 (D-06) */
    @Transactional
    public void delete(Long postId, LoginUser me) {
        postRepository.delete(getMyDraft(postId, me));
    }

    /** API 7.6 발행: 화면의 최신 내용으로 저장 + 발행을 한 트랜잭션에서 (D-05). 이번엔 제목·본문 필수 */
    @Transactional
    public PostCreateResponse publish(Long postId, LoginUser me, PostRequest request) {
        Post draft = getMyDraft(postId, me);
        draft.publishDraft(categoryService.getCategoryForPost(draft.getBlog(), request.categoryId()),
                topicService.getTopicOrNull(request.topicId()),
                request.title().strip(), request.content(), request.thumbnailUrl(),
                Visibility.orPublic(request.visibility()),
                tagService.resolveTags(request.tags()),
                TimeCapsulePolicy.openAtOrNull(request.openAt()));
        postLinkService.sync(draft);
        return new PostCreateResponse(draft.getId(), me.username());
    }

    /** 내 임시저장 글만. 남의 것이든 발행된 글이든 모두 404예요 */
    private Post getMyDraft(Long postId, LoginUser me) {
        return postRepository.findById(postId)
                .filter(post -> post.getStatus() == PostStatus.DRAFT)
                .filter(post -> post.isOwnedBy(me.id()))
                .orElseThrow(() -> new BusinessException(ErrorCode.DRAFT_NOT_FOUND));
    }
}
