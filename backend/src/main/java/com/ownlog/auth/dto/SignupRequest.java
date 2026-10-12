package com.ownlog.auth.dto;

import com.ownlog.user.domain.PasswordPolicy;
import com.ownlog.user.domain.UsernamePolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import com.ownlog.global.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** API 명세서 4.1 요청. 형식 검증은 여기서, 중복·예약어·비밀번호 확인 일치는 서비스에서 해요. */
public record SignupRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 아니에요.")
        @Size(max = 255, message = "이메일은 255자 이하여야 해요.")
        String email,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        @MaxUtf8Bytes(value = PasswordPolicy.MAX_BYTES, message = PasswordPolicy.BYTES_MESSAGE)
        String password,

        @NotBlank(message = "비밀번호 확인을 입력해 주세요.")
        String passwordConfirm,

        @NotBlank(message = "아이디를 입력해 주세요.")
        @Pattern(regexp = UsernamePolicy.REGEX, message = "아이디는 영문 소문자, 숫자, _ 3~20자예요.")
        String username,

        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(min = 2, max = 20, message = "닉네임은 2~20자예요.")
        String nickname
) {
}
