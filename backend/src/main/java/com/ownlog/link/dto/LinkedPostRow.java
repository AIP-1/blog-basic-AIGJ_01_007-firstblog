package com.ownlog.link.dto;

import java.time.Instant;

/** 백링크·자동완성 쿼리 결과 한 줄 (select new용) */
public record LinkedPostRow(Long id, String title, String username, String nickname, Instant publishedAt) {
}
