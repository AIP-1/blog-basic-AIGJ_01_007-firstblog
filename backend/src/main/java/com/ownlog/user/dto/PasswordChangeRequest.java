package com.ownlog.user.dto;

import com.ownlog.user.domain.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import com.ownlog.global.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Pattern;

/** API 4.6 요청. 비밀번호가 없는 소셜 회원은 currentPassword 없이 처음 비밀번호를 정해요 */
public record PasswordChangeRequest(
        // 소셜 로그인으로만 가입해서 비밀번호가 없으면 비워도 돼요 (서비스에서 확인)
        String currentPassword,

        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        @MaxUtf8Bytes(value = PasswordPolicy.MAX_BYTES, message = PasswordPolicy.BYTES_MESSAGE)
        String newPassword,

        @NotBlank(message = "새 비밀번호 확인을 입력해 주세요.")
        String newPasswordConfirm
) {
}
