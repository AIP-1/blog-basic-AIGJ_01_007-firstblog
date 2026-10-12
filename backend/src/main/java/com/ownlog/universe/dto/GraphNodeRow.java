package com.ownlog.universe.dto;

import java.time.Instant;

/** 그래프 별 쿼리 결과 한 줄 (select new용). username·nickname은 서비스 전체 우주에서 블로그(은하)를 나눌 때 써요 */
public record GraphNodeRow(Long id, String title, String topicSlug, int likeCount, Instant publishedAt, Instant sealedAt,
                           String username, String nickname) {

    /** 아직 열리지 않은 타임캡슐 */
    public boolean isSealed(Instant now) {
        return sealedAt != null && publishedAt.isAfter(now);
    }

    /** 쓴 시각: 타임캡슐은 봉인한 시각 (타임랩스 GR-05, 잔디 AC-04와 같은 기준) */
    public Instant writtenAt() {
        return sealedAt != null ? sealedAt : publishedAt;
    }
}
