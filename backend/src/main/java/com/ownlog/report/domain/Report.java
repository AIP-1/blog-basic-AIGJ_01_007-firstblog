package com.ownlog.report.domain;

import com.ownlog.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 신고 (ERD 3.8). 대상은 글 또는 댓글 중 정확히 하나예요 (DB CHECK ck_reports_one_target).
 * 신고는 "어디에 들어왔는지"만 기록하면 돼서 연관관계 대신 id만 들고 있어요.
 */
@Getter
@Entity
@Table(name = "reports")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 글 신고일 때 */
    private Long postId;

    /** 댓글 신고일 때 */
    private Long commentId;

    @Column(nullable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportReason reason;

    @Column(length = 500)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    public static Report forPost(Long postId, Long reporterId, ReportReason reason, String detail) {
        return create(postId, null, reporterId, reason, detail);
    }

    public static Report forComment(Long commentId, Long reporterId, ReportReason reason, String detail) {
        return create(null, commentId, reporterId, reason, detail);
    }

    private static Report create(Long postId, Long commentId, Long reporterId, ReportReason reason, String detail) {
        Report report = new Report();
        report.postId = postId;
        report.commentId = commentId;
        report.reporterId = reporterId;
        report.reason = reason;
        report.detail = detail;
        report.status = ReportStatus.RECEIVED;
        return report;
    }
}
