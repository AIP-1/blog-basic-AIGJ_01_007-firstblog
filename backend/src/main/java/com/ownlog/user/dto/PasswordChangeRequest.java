package com.ownlog.user.dto;

import com.ownlog.user.domain.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import com.ownlog.global.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Pattern;

/** API 4.6 요청 */
public record PasswordChangeRequest(
        @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        @MaxUtf8Bytes(value = PasswordPolicy.MAX_BYTES, message = PasswordPolicy.BYTES_MESSAGE)
        String newPassword,

        @NotBlank(message = "새 비밀번호 확인을 입력해 주세요.")
        String newPasswordConfirm
) {
}
