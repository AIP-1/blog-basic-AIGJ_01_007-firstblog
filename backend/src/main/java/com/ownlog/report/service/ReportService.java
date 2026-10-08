package com.ownlog.report.service;

import com.ownlog.comment.domain.Comment;
import com.ownlog.comment.repository.CommentRepository;
import com.ownlog.global.error.BusinessException;
import com.ownlog.global.error.ErrorCode;
import com.ownlog.global.error.ErrorResponse.FieldError;
import com.ownlog.global.security.LoginUser;
import com.ownlog.post.domain.Post;
import com.ownlog.post.service.PostQueryService;
import com.ownlog.report.domain.Report;
import com.ownlog.report.domain.ReportReason;
import com.ownlog.report.dto.ReportRequest;
import com.ownlog.report.dto.ReportResponse;
import com.ownlog.report.repository.ReportRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고 (API 10.1, 10.2). MVP는 접수만 해요 (R-03).
 * "확인 후 저장" 사이에 같은 사람이 동시에 두 번 신고하면 DB UNIQUE가 막고,
 * GlobalExceptionHandler가 ALREADY_REPORTED(409)로 바꿔요.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final CommentRepository commentRepository;
    private final PostQueryService postQueryService;

    /** API 10.1 글 신고 */
    @Transactional
    public ReportResponse reportPost(Long postId, LoginUser me, ReportRequest request) {
        Post post = postQueryService.getViewablePost(postId, me);   // 볼 수 없으면 POST_NOT_FOUND
        if (post.isOwnedBy(me.id())) {
            throw new BusinessException(ErrorCode.CANNOT_REPORT_OWN_CONTENT);
        }
        if (reportRepository.existsByPostIdAndReporterId(postId, me.id())) {
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }
        String detail = requireDetailIfEtc(request);
        Report saved = reportRepository.save(Report.forPost(postId, me.id(), request.reason(), detail));
        return new ReportResponse(saved.getId());
    }

    /** API 10.2 댓글 신고 */
    @Transactional
    public ReportResponse reportComment(Long commentId, LoginUser me, ReportRequest request) {
        Comment comment = commentRepository.findById(commentId)
                .filter(c -> postQueryService.isViewable(c.getPost(), me))   // 볼 수 없는 글의 댓글도 404
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (comment.isDeleted()) {
            throw new BusinessException(ErrorCode.CANNOT_REPORT_DELETED_COMMENT);
        }
        if (comment.isWrittenBy(me.id())) {
            throw new BusinessException(ErrorCode.CANNOT_REPORT_OWN_CONTENT);
        }
        if (reportRepository.existsByCommentIdAndReporterId(commentId, me.id())) {
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }
        String detail = requireDetailIfEtc(request);
        Report saved = reportRepository.save(Report.forComment(commentId, me.id(), request.reason(), detail));
        return new ReportResponse(saved.getId());
    }

    /** "기타"는 상세 사유 필수 (R-01). DB에도 같은 CHECK가 있지만, 사용자에게는 어느 칸이 문제인지 알려줘요 */
    private String requireDetailIfEtc(ReportRequest request) {
        String detail = request.normalizedDetail();
        if (request.reason() == ReportReason.ETC && detail == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    List.of(new FieldError("detail", "기타 사유를 적어 주세요.")));
        }
        return detail;
    }
}
