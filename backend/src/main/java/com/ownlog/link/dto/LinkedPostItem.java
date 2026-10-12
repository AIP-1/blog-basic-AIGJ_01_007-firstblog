package com.ownlog.link.dto;

import com.ownlog.global.util.KstTime;
import java.time.OffsetDateTime;

/** API 13.1 백링크, 13.2 자동완성 한 줄 */
public record LinkedPostItem(Long id, String title, String username, String nickname, OffsetDateTime publishedAt) {

    public static LinkedPostItem from(LinkedPostRow row) {
        return new LinkedPostItem(row.id(), row.title(), row.username(), row.nickname(), KstTime.of(row.publishedAt()));
    }
}
