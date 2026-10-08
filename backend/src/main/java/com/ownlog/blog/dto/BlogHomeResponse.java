package com.ownlog.blog.dto;

import com.ownlog.blog.domain.Blog;
import com.ownlog.user.domain.User;

/** API 명세서 5.1 응답 */
public record BlogHomeResponse(Long blogId, String name, String description, Owner owner, boolean isOwner) {

    public record Owner(String username, String nickname, String bio, String profileImageUrl) {
    }

    public static BlogHomeResponse of(Blog blog, boolean isOwner) {
        User user = blog.getUser();
        return new BlogHomeResponse(blog.getId(), blog.getName(), blog.getDescription(),
                new Owner(user.getUsername(), user.getNickname(), user.getBio(), user.getProfileImageUrl()),
                isOwner);
    }
}
