package com.ownlog.global.security;

import com.ownlog.user.domain.UserRole;

/**
 * 로그인한 회원 정보. 컨트롤러에서 @AuthenticationPrincipal LoginUser me 로 받아요.
 * 방문자(토큰 없음)면 me는 null이에요 → 🔓 API에서 "주인인가?" 판단에 써요.
 */
public record LoginUser(Long id, String username, UserRole role) {
}
