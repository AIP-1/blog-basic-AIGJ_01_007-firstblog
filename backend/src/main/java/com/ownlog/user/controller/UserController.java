package com.ownlog.user.controller;

import com.ownlog.global.security.LoginUser;
import com.ownlog.user.dto.MeResponse;
import com.ownlog.user.dto.PasswordChangeRequest;
import com.ownlog.user.dto.ProfileUpdateRequest;
import com.ownlog.user.dto.WithdrawRequest;
import com.ownlog.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API 4.4 ~ 4.7 내 정보 (모두 🔒, me는 항상 있어요) */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 4.4 내 정보 */
    @GetMapping
    public MeResponse getMe(@AuthenticationPrincipal LoginUser me) {
        return userService.getMe(me.id());
    }

    /** 4.5 프로필 수정 */
    @PutMapping
    public MeResponse updateProfile(@AuthenticationPrincipal LoginUser me,
                                    @Valid @RequestBody ProfileUpdateRequest request) {
        return userService.updateProfile(me.id(), request);
    }

    /** 4.6 비밀번호 변경 → 204 */
    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal LoginUser me,
                                               @Valid @RequestBody PasswordChangeRequest request) {
        userService.changePassword(me.id(), request);
        return ResponseEntity.noContent().build();
    }

    /** 4.7 탈퇴 → 204. 본문에 비밀번호를 담아요 (axios는 data 옵션) */
    @DeleteMapping
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal LoginUser me,
                                         @Valid @RequestBody WithdrawRequest request) {
        userService.withdraw(me.id(), request);
        return ResponseEntity.noContent().build();
    }
}
