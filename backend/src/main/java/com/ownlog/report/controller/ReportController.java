package com.ownlog.report.controller;

import com.ownlog.global.security.LoginUser;
import com.ownlog.report.dto.ReportRequest;
import com.ownlog.report.dto.ReportResponse;
import com.ownlog.report.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** API 10.1, 10.2 신고 (🔒) */
@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/api/posts/{postId}/reports")
    public ResponseEntity<ReportResponse> reportPost(@PathVariable Long postId, @AuthenticationPrincipal LoginUser me,
                                                     @Valid @RequestBody ReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.reportPost(postId, me, request));
    }

    @PostMapping("/api/comments/{commentId}/reports")
    public ResponseEntity<ReportResponse> reportComment(@PathVariable Long commentId,
                                                        @AuthenticationPrincipal LoginUser me,
                                                        @Valid @RequestBody ReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.reportComment(commentId, me, request));
    }
}
