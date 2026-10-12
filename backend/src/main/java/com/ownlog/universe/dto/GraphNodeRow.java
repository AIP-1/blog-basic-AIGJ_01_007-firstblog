package com.ownlog.universe.dto;

import java.time.Instant;

/** 그래프 별 쿼리 결과 한 줄 (select new용) */
public record GraphNodeRow(Long id, String title, String topicSlug, int likeCount, Instant publishedAt, Instant sealedAt) {

    /** 아직 열리지 않은 타임캡슐 */
    public boolean isSealed(Instant now) {
        return sealedAt != null && publishedAt.isAfter(now);
    }
}
