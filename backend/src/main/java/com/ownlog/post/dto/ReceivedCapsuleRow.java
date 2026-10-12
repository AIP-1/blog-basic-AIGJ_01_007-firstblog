package com.ownlog.post.dto;

import com.ownlog.user.domain.UserStatus;
import java.time.Instant;

/** 12.3 받은 타임캡슐 쿼리 결과 한 줄 (select new용) */
public record ReceivedCapsuleRow(Long id, String title, String username, String nickname, String profileImageUrl,
                                 UserStatus userStatus, Instant sealedAt, Instant openAt) {
}
