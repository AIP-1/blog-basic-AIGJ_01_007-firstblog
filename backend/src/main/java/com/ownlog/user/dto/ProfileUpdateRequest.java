package com.ownlog.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** API 4.5 요청. PUT이라 폼 전체를 보내고, null이면 그 값을 지워요 */
public record ProfileUpdateRequest(
        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(min = 2, max = 20, message = "닉네임은 2~20자예요.")
        String nickname,

        @Size(max = 100, message = "한 줄 소개는 100자 이하여야 해요.")
        String bio,

        @Size(max = 500, message = "프로필 사진 주소는 500자 이하여야 해요.")
        @Pattern(regexp = "^/uploads/.*", message = "프로필 사진은 업로드한 이미지만 쓸 수 있어요.")
        String profileImageUrl
) {
    /** 공백뿐인 소개는 지운 것으로 봐요 */
    public String normalizedBio() {
        return bio == null || bio.isBlank() ? null : bio.strip();
    }
}
