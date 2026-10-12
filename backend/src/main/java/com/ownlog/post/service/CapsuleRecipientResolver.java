package com.ownlog.post.service;

import com.ownlog.blog.service.BlogService;
import com.ownlog.global.error.BusinessException;
import com.ownlog.global.error.ErrorCode;
import com.ownlog.global.security.LoginUser;
import com.ownlog.user.domain.User;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 받는 사람 캡슐의 받는 사람 확인 (TC-07): 타임캡슐일 때만, 활동 중인 다른 회원만 */
@Component
@RequiredArgsConstructor
class CapsuleRecipientResolver {

    private final BlogService blogService;

    User resolve(String recipientUsername, Instant openAt, LoginUser me) {
        if (recipientUsername == null || recipientUsername.isBlank()) {
            return null;
        }
        if (openAt == null) {
            throw new BusinessException(ErrorCode.RECIPIENT_REQUIRES_CAPSULE);
        }
        String username = recipientUsername.strip().replaceFirst("^@", "");
        if (username.equalsIgnoreCase(me.username())) {
            throw new BusinessException(ErrorCode.INVALID_RECIPIENT);
        }
        try {
            return blogService.getActiveBlog(username).getUser();   // 없거나 탈퇴한 회원이면 USER_NOT_FOUND
        } catch (BusinessException e) {
            throw new BusinessException(ErrorCode.INVALID_RECIPIENT);
        }
    }
}
