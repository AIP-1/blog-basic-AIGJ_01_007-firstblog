package com.ownlog.user.dto;

import jakarta.validation.constraints.NotBlank;

/** API 4.7 요청. 되돌릴 수 없어서 비밀번호를 다시 확인해요 */
public record WithdrawRequest(
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        String password
) {
}
