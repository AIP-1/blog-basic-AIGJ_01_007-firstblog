package com.ownlog.post.dto;

import java.time.Instant;

/** 12.1 쿼리 결과 한 줄 (select new용) */
public record TimeCapsuleRow(Long id, String title, Instant sealedAt, Instant openAt) {
}
