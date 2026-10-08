package com.ownlog.user.dto;

import com.ownlog.user.domain.User;

/** API 명세서 2.1 작성자 정보. 탈퇴한 회원이면 아이디를 숨기고 "탈퇴한 사용자"로 보여줘요 (U-06) */
public record UserSummary(String username, String nickname, String profileImageUrl) {

    public static UserSummary from(User user) {
        if (!user.isActive()) {
            return new UserSummary(null, User.DELETED_NICKNAME, null);
        }
        return new UserSummary(user.getUsername(), user.getNickname(), user.getProfileImageUrl());
    }
}
