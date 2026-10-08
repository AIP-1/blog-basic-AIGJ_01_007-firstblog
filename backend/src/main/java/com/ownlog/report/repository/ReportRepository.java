package com.ownlog.report.repository;

import com.ownlog.report.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByPostIdAndReporterId(Long postId, Long reporterId);

    boolean existsByCommentIdAndReporterId(Long commentId, Long reporterId);
}
