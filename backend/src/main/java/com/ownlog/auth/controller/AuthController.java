package com.ownlog.auth.controller;

import com.ownlog.auth.dto.LoginRequest;
import com.ownlog.auth.dto.LoginResponse;
import com.ownlog.auth.dto.SignupRequest;
import com.ownlog.auth.dto.SignupResponse;
import com.ownlog.auth.dto.UsernameCheckResponse;
import com.ownlog.auth.oauth.OAuthClientRegistrations;
import java.util.List;
import java.util.Map;
import com.ownlog.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OAuthClientRegistrations oauthClientRegistrations;

    /** API 4.1 회원가입 → 201 */
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    /** API 4.2 로그인 */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** API 4.3 아이디 사용 가능 여부 → 항상 200 (불가능하면 reason으로 알려줘요) */
    @GetMapping("/check-username")
    public UsernameCheckResponse checkUsername(@RequestParam String username) {
        return authService.checkUsername(username);
    }

    /** API 4.9 켜져 있는 소셜 로그인 (키를 등록한 서비스만, 버튼 순서대로) */
    @GetMapping("/oauth/providers")
    public Map<String, List<String>> oauthProviders() {
        return Map.of("providers", oauthClientRegistrations.enabledProviderIds());
    }

    /** API 4.10 소셜 로그인 1회용 코드 → 토큰 */
    @PostMapping("/oauth/exchange")
    public LoginResponse exchange(@RequestBody Map<String, String> body) {
        return authService.exchangeOAuthCode(body.get("code"));
    }
}
