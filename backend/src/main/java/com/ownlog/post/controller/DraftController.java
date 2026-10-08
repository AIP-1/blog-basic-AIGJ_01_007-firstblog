package com.ownlog.post.controller;

import com.ownlog.global.common.PageCondition;
import com.ownlog.global.common.PageResponse;
import com.ownlog.global.security.LoginUser;
import com.ownlog.post.dto.DraftDetailResponse;
import com.ownlog.post.dto.DraftItem;
import com.ownlog.post.dto.DraftRequest;
import com.ownlog.post.dto.DraftSaveResponse;
import com.ownlog.post.dto.PostCreateResponse;
import com.ownlog.post.dto.PostRequest;
import com.ownlog.post.service.DraftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API 7.1 ~ 7.6 임시저장 (모두 🔒) */
@RestController
@RequestMapping("/api/drafts")
@RequiredArgsConstructor
public class DraftController {

    private final DraftService draftService;

    /** 7.1 처음 임시저장 → 201 */
    @PostMapping
    public ResponseEntity<DraftSaveResponse> create(@AuthenticationPrincipal LoginUser me,
                                                    @Valid @RequestBody DraftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(draftService.create(me, request));
    }

    /** 7.2 다시 임시저장 (덮어쓰기) */
    @PutMapping("/{postId}")
    public DraftSaveResponse save(@PathVariable Long postId, @AuthenticationPrincipal LoginUser me,
                                  @Valid @RequestBody DraftRequest request) {
        return draftService.save(postId, me, request);
    }

    /** 7.3 임시저장 목록 */
    @GetMapping
    public PageResponse<DraftItem> getDrafts(@AuthenticationPrincipal LoginUser me,
                                             @Valid @ModelAttribute PageCondition page) {
        return draftService.getDrafts(me, page);
    }

    /** 7.4 불러오기 */
    @GetMapping("/{postId}")
    public DraftDetailResponse getDraft(@PathVariable Long postId, @AuthenticationPrincipal LoginUser me) {
        return draftService.getDraft(postId, me);
    }

    /** 7.5 삭제 → 204 */
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> delete(@PathVariable Long postId, @AuthenticationPrincipal LoginUser me) {
        draftService.delete(postId, me);
        return ResponseEntity.noContent().build();
    }

    /** 7.6 발행 (제목·본문 필수) */
    @PostMapping("/{postId}/publish")
    public PostCreateResponse publish(@PathVariable Long postId, @AuthenticationPrincipal LoginUser me,
                                      @Valid @RequestBody PostRequest request) {
        return draftService.publish(postId, me, request);
    }
}
