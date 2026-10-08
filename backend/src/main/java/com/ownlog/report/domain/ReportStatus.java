package com.ownlog.report.domain;

/** 신고 처리 상태. MVP에서는 RECEIVED만 쓰고, 나머지는 2차 관리자 기능에서 써요 (R-03) */
public enum ReportStatus {
    RECEIVED, RESOLVED, REJECTED
}
