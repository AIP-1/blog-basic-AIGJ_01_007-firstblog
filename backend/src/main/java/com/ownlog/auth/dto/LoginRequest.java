package com.ownlog.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** API 명세서 4.2 요청. 이메일 형식은 검사하지 않아요 (틀리면 그냥 로그인 실패로 응답). */
public record LoginRequest(
        @NotBlank(message = "이메일을 입력해 주세요.") String email,
        @NotBlank(message = "비밀번호를 입력해 주세요.") String password
) {
}
