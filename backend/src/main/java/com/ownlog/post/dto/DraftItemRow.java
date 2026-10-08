package com.ownlog.post.dto;

import java.time.Instant;

/** 임시저장 목록 쿼리 결과 한 줄 (본문은 읽지 않아요) */
public record DraftItemRow(Long id, String title, String summary, Instant updatedAt) {
}
