package com.ownlog.post.dto;

import com.ownlog.global.util.KstTime;
import java.time.OffsetDateTime;

/** API 12.1 곧 열리는 타임캡슐 한 줄: 제목과 봉인·개봉 시각만 (본문 정보는 없어요) */
public record TimeCapsuleItem(Long id, String title, OffsetDateTime sealedAt, OffsetDateTime openAt) {

    public static TimeCapsuleItem from(TimeCapsuleRow row) {
        return new TimeCapsuleItem(row.id(), row.title(), KstTime.of(row.sealedAt()), KstTime.of(row.openAt()));
    }
}
