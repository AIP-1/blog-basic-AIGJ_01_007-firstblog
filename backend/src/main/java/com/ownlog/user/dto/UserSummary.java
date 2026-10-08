package com.ownlog.user.dto;

import com.ownlog.user.domain.User;
import com.ownlog.user.domain.UserStatus;

/** API 명세서 2.1 작성자 정보. 탈퇴한 회원이면 아이디를 숨기고 "탈퇴한 사용자"로 보여줘요 (U-06) */
public record UserSummary(String username, String nickname, String profileImageUrl) {

    public static UserSummary from(User user) {
        if (!user.isActive()) {
            return new UserSummary(null, User.DELETED_NICKNAME, null);
        }
        return new UserSummary(user.getUsername(), user.getNickname(), user.getProfileImageUrl());
    }

    /** 쿼리에서 컬럼만 가져온 경우 (글 카드 목록) */
    public static UserSummary of(String username, String nickname, String profileImageUrl, UserStatus status) {
        if (status != UserStatus.ACTIVE) {
            return new UserSummary(null, User.DELETED_NICKNAME, null);
        }
        return new UserSummary(username, nickname, profileImageUrl);
    }
}
