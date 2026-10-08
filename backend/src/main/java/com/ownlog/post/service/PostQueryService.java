package com.ownlog.post.service;

import com.ownlog.blog.domain.Blog;
import com.ownlog.blog.service.BlogService;
import com.ownlog.global.error.BusinessException;
import com.ownlog.global.error.ErrorCode;
import com.ownlog.global.security.LoginUser;
import com.ownlog.post.domain.Post;
import com.ownlog.post.dto.PostDetailResponse;
import com.ownlog.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 글 읽기 전용 기능 (상세, 목록). 쓰기는 PostService에 있어요. */
@Service
@RequiredArgsConstructor
public class PostQueryService {

    private final PostRepository postRepository;
    private final BlogService blogService;

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
}
