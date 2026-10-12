package com.ownlog.reaction.service;

import com.ownlog.global.common.PageCondition;
import com.ownlog.global.common.PageResponse;
import com.ownlog.global.error.BusinessException;
import com.ownlog.global.error.ErrorCode;
import com.ownlog.global.security.LoginUser;
import com.ownlog.post.repository.PostRepository;
import com.ownlog.post.service.PostQueryService;
import com.ownlog.reaction.dto.BookmarkResponse;
import com.ownlog.reaction.dto.BookmarkedPostCard;
import com.ownlog.reaction.dto.LikeResponse;
import com.ownlog.reaction.repository.BookmarkRepository;
import com.ownlog.reaction.repository.PostLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요·북마크 (API 9.1, 9.2, 4.8). 누르기(PUT)와 취소(DELETE)를 따로 둬서 멱등해요.
 * 좋아요 수는 행이 "실제로" 바뀌었을 때만 DB가 직접 더하고 빼요 (ERD 3.7).
 */
@Service
@RequiredArgsConstructor
public class ReactionService {

    private final PostLikeRepository postLikeRepository;
    private final BookmarkRepository bookmarkRepository;
    private final PostRepository postRepository;
    private final PostQueryService postQueryService;

    /** API 9.1 좋아요 누르기 (본인 글도 가능, C-03) */
    @Transactional
    public LikeResponse like(Long postId, LoginUser me) {
        postQueryService.getViewablePost(postId, me);
        if (postLikeRepository.insertIfAbsent(me.id(), postId) == 1) {   // 실제로 새로 눌렀을 때만
            postRepository.increaseLikeCount(postId);
        }
        return new LikeResponse(true, postRepository.findLikeCount(postId));
    }

    /** API 9.1 좋아요 취소. 누른 뒤 글이 비공개가 됐어도 내 좋아요는 취소할 수 있어요 */
    @Transactional
    public LikeResponse unlike(Long postId, LoginUser me) {
        getPublishedPost(postId);
        if (postLikeRepository.deleteIfPresent(me.id(), postId) == 1) {  // 실제로 취소했을 때만
            postRepository.decreaseLikeCount(postId);
        }
        return new LikeResponse(false, postRepository.findLikeCount(postId));
    }

    /** API 9.2 북마크 추가 */
    @Transactional
    public BookmarkResponse bookmark(Long postId, LoginUser me) {
        postQueryService.getViewablePost(postId, me);
        bookmarkRepository.insertIfAbsent(me.id(), postId);
        return new BookmarkResponse(true);
    }

    /** API 9.2 북마크 해제. 글이 비공개가 됐어도 내 북마크는 지울 수 있어요 */
    @Transactional
    public BookmarkResponse unbookmark(Long postId, LoginUser me) {
        getPublishedPost(postId);
        bookmarkRepository.deleteIfPresent(me.id(), postId);
        return new BookmarkResponse(false);
    }

    /** 취소용: 발행된 글이면 돼요 (내 좋아요·북마크 행만 지우니 볼 수 있는지는 따지지 않아요) */
    private void getPublishedPost(Long postId) {
        postRepository.findById(postId)
                .filter(post -> post.isPublished())
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    /** API 4.8 내 북마크 목록: 북마크한 최신순, 지금 볼 수 없는 글은 빼요 (K-02) */
    @Transactional(readOnly = true)
    public PageResponse<BookmarkedPostCard> getMyBookmarks(LoginUser me, PageCondition page) {
        return PageResponse.from(bookmarkRepository.findMyBookmarks(me.id(), page.toPageable(Sort.unsorted()))
                .map(BookmarkedPostCard::from));
    }
}
