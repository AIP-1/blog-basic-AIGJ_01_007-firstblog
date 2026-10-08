package com.ownlog.blog.service;

import com.ownlog.blog.domain.Blog;
import com.ownlog.blog.dto.BlogHomeResponse;
import com.ownlog.blog.dto.BlogResponse;
import com.ownlog.blog.dto.BlogUpdateRequest;
import com.ownlog.blog.repository.BlogRepository;
import com.ownlog.global.error.BusinessException;
import com.ownlog.global.error.ErrorCode;
import com.ownlog.global.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BlogService {

    private final BlogRepository blogRepository;

    /** API 5.1 블로그 홈. me가 null이면 방문자예요. */
    @Transactional(readOnly = true)
    public BlogHomeResponse getBlogHome(String username, LoginUser me) {
        Blog blog = getActiveBlog(username);
        return BlogHomeResponse.of(blog, isOwner(blog, me));
    }

    /** API 5.2 블로그 이름·소개 수정 */
    @Transactional
    public BlogResponse updateBlog(Long blogId, LoginUser me, BlogUpdateRequest request) {
        Blog blog = getOwnedBlog(blogId, me);
        blog.update(request.name().strip(), request.description());
        return BlogResponse.from(blog);
    }

    /** /@{username}의 블로그 (탈퇴 회원이면 404). 다른 서비스도 함께 써요. */
    public Blog getActiveBlog(String username) {
        return blogRepository.findActiveByUsername(username).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /** 블로그는 공개 공간이라 주인이 아니면 403이에요. 카테고리 생성·순서 변경에서도 써요. */
    public Blog getOwnedBlog(Long blogId, LoginUser me) {
        Blog blog = blogRepository.findById(blogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_NOT_FOUND));
        if (!blog.isOwnedBy(me.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return blog;
    }

    public boolean isOwner(Blog blog, LoginUser me) {
        return me != null && blog.isOwnedBy(me.id());
    }
}
