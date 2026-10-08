package com.ownlog.post.dto;

/** API 명세서 6.6 응답. 프론트엔드는 /@{username}/posts/{id}로 이동해요. */
public record PostCreateResponse(Long id, String username) {
}
