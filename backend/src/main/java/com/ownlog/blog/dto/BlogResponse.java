package com.ownlog.blog.dto;

import com.ownlog.blog.domain.Blog;

/** API 명세서 5.2 응답 */
public record BlogResponse(Long id, String name, String description) {

    public static BlogResponse from(Blog blog) {
        return new BlogResponse(blog.getId(), blog.getName(), blog.getDescription());
    }
}
