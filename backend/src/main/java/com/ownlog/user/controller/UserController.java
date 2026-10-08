package com.ownlog.user.controller;

import com.ownlog.global.security.LoginUser;
import com.ownlog.user.dto.MeResponse;
import com.ownlog.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** API 4.4 내 정보. 🔒 주소라서 me는 항상 있어요 (없으면 Security가 먼저 401로 막아요) */
    @GetMapping("/me")
    public MeResponse getMe(@AuthenticationPrincipal LoginUser me) {
        return userService.getMe(me.id());
    }
}
