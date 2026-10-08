package com.ownlog.reaction.controller;

import com.ownlog.global.common.PageCondition;
import com.ownlog.global.common.PageResponse;
import com.ownlog.global.security.LoginUser;
import com.ownlog.reaction.dto.BookmarkResponse;
import com.ownlog.reaction.dto.BookmarkedPostCard;
import com.ownlog.reaction.dto.LikeResponse;
import com.ownlog.reaction.service.ReactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/** API 9.1, 9.2, 4.8 (모두 🔒) */
@RestController
@RequiredArgsConstructor
public class ReactionController {

    private final ReactionService reactionService;

    @PutMapping("/api/posts/{postId}/like")
    public LikeResponse like(@PathVariable Long postId, @AuthenticationPrincipal LoginUser me) {
        return reactionService.like(postId, me);
    }

    @DeleteMapping("/api/posts/{postId}/like")
    public LikeResponse unlike(@PathVariable Long postId, @AuthenticationPrincipal LoginUser me) {
        return reactionService.unlike(postId, me);
    }

    @PutMapping("/api/posts/{postId}/bookmark")
    public BookmarkResponse bookmark(@PathVariable Long postId, @AuthenticationPrincipal LoginUser me) {
        return reactionService.bookmark(postId, me);
    }

    @DeleteMapping("/api/posts/{postId}/bookmark")
    public BookmarkResponse unbookmark(@PathVariable Long postId, @AuthenticationPrincipal LoginUser me) {
        return reactionService.unbookmark(postId, me);
    }

    /** API 4.8 내 북마크 목록 */
    @GetMapping("/api/users/me/bookmarks")
    public PageResponse<BookmarkedPostCard> getMyBookmarks(@AuthenticationPrincipal LoginUser me,
                                                           @Valid @ModelAttribute PageCondition page) {
        return reactionService.getMyBookmarks(me, page);
    }
}
