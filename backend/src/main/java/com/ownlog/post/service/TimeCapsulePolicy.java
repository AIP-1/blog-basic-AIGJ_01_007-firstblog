package com.ownlog.post.service;

import com.ownlog.global.error.BusinessException;
import com.ownlog.global.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/** 타임캡슐 개봉 시각 규칙 (TC-02): 지금부터 1분 뒤 ~ 10년 뒤 */
final class TimeCapsulePolicy {

    static final Duration MIN_DELAY = Duration.ofMinutes(1);   // 발표 시연용 최소값
    static final Duration MAX_DELAY = Duration.ofDays(3653);   // 10년 (윤년 포함)

    private TimeCapsulePolicy() {
    }

    /** null이면 일반 글(null), 아니면 검사한 개봉 시각. 초 아래는 버려서 화면의 분 단위와 맞춰요 */
    static Instant openAtOrNull(OffsetDateTime openAt) {
        if (openAt == null) {
            return null;
        }
        Instant at = openAt.toInstant().truncatedTo(ChronoUnit.SECONDS);
        Instant now = Instant.now();
        if (at.isBefore(now.plus(MIN_DELAY).minusSeconds(5)) || at.isAfter(now.plus(MAX_DELAY))) {
            throw new BusinessException(ErrorCode.INVALID_OPEN_AT);
        }
        return at;
    }
}
