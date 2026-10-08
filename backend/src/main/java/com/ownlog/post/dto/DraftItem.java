package com.ownlog.post.dto;

import com.ownlog.global.util.KstTime;
import java.time.OffsetDateTime;

/** API 7.3 목록 항목. title이 null이면 화면에 "제목 없음"으로 보여줘요 (D-03) */
public record DraftItem(Long id, String title, String summary, OffsetDateTime updatedAt) {

    public static DraftItem from(DraftItemRow row) {
        return new DraftItem(row.id(), row.title(), row.summary(), KstTime.of(row.updatedAt()));
    }
}
