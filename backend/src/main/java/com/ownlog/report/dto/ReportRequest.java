package com.ownlog.report.dto;

import com.ownlog.report.domain.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** API 10.1, 10.2 요청 */
public record ReportRequest(
        @NotNull(message = "신고 사유를 선택해 주세요.")
        ReportReason reason,

        @Size(max = 500, message = "상세 사유는 500자 이하여야 해요.")
        String detail
) {
    /** 공백뿐이면 null로 저장해요 */
    public String normalizedDetail() {
        return detail == null || detail.isBlank() ? null : detail.strip();
    }
}
