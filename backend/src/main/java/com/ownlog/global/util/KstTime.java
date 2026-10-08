package com.ownlog.global.util;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** 엔티티의 Instant를 API 응답용 한국 시간(+09:00)으로 바꿔요 (API 명세서 1.1). */
public final class KstTime {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private KstTime() {
    }

    /** null이면 null을 돌려줘요 (예: 수정한 적 없는 글의 editedAt). */
    public static OffsetDateTime of(Instant instant) {
        return instant == null ? null : instant.atZone(KST).toOffsetDateTime();
    }
}
