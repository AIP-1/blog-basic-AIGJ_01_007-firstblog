package com.ownlog.user.dto;

/**
 * API 4.7 요청. 되돌릴 수 없어서 한 번 더 확인해요.
 * 비밀번호가 있으면 password, 소셜 로그인으로만 가입해서 비밀번호가 없으면 내 아이디(username)를 입력해요.
 */
public record WithdrawRequest(String password, String username) {
}
