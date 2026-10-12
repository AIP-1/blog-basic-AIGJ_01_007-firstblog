package com.ownlog.post.dto;

import com.ownlog.global.util.KstTime;
import com.ownlog.user.dto.UserSummary;
import java.time.Instant;
import java.time.OffsetDateTime;

/** API 12.3 받은 타임캡슐 한 줄 (TC-09). opened면 상세로 들어가서 본문을 볼 수 있어요 */
public record ReceivedCapsuleItem(Long id, String title, UserSummary sender,
                                  OffsetDateTime sealedAt, OffsetDateTime openAt, boolean opened) {

    public static ReceivedCapsuleItem from(ReceivedCapsuleRow row, Instant now) {
        return new ReceivedCapsuleItem(row.id(), row.title(),
                UserSummary.of(row.username(), row.nickname(), row.profileImageUrl(), row.userStatus()),
                KstTime.of(row.sealedAt()), KstTime.of(row.openAt()), !row.openAt().isAfter(now));
    }
}
