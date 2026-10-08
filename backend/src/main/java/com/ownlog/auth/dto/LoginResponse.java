package com.ownlog.auth.dto;

import com.ownlog.user.domain.User;

/** API 명세서 4.2 응답 */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserInfo user) {

    public record UserInfo(Long id, String username, String nickname, String profileImageUrl, Long blogId) {
    }

    public static LoginResponse of(String accessToken, long expiresIn, User user, Long blogId) {
        return new LoginResponse(accessToken, "Bearer", expiresIn, new UserInfo(
                user.getId(), user.getUsername(), user.getNickname(), user.getProfileImageUrl(), blogId));
    }
}
