package com.ownlog.post.dto;

import java.time.OffsetDateTime;

/** API 명세서 6.7 응답. 내용이 바뀌지 않았으면 editedAt은 null일 수 있어요. */
public record PostUpdateResponse(Long id, String username, OffsetDateTime editedAt) {
}
