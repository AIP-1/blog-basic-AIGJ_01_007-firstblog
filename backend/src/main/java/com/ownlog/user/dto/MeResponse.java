package com.ownlog.user.dto;

import com.ownlog.blog.domain.Blog;
import com.ownlog.global.util.KstTime;
import com.ownlog.user.domain.User;
import com.ownlog.user.domain.UserRole;
import java.time.OffsetDateTime;

/** API 명세서 4.4 응답 */
public record MeResponse(
        Long id, String email, String username, String nickname, String bio,
        String profileImageUrl, UserRole role, BlogInfo blog, OffsetDateTime createdAt,
        boolean passwordSet   // false = 소셜 로그인으로만 가입 (비밀번호 변경·탈퇴 화면이 달라요)
) {
    public record BlogInfo(Long id, String name, String description) {
    }

    public static MeResponse of(User user, Blog blog) {
        return new MeResponse(user.getId(), user.getEmail(), user.getUsername(), user.getNickname(),
                user.getBio(), user.getProfileImageUrl(), user.getRole(),
                new BlogInfo(blog.getId(), blog.getName(), blog.getDescription()),
                KstTime.of(user.getCreatedAt()), user.isPasswordSet());
    }
}
