package com.ownlog.auth.oauth;

import com.ownlog.user.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 소셜 로그인의 마지막 단계. 서비스에서 돌아오면(/login/oauth2/code/kakao 등) Spring이 토큰·회원 정보를 받아 오고,
 * 여기서 OwnLog 회원을 찾거나 만든 뒤 화면(/oauth/callback)으로 1회용 코드를 들고 돌아가요.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuthLoginHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private final SocialLoginService socialLoginService;
    private final OAuthLoginCodeStore codeStore;
    private final OAuthProperties properties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        endSession(request);
        try {
            OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
            SocialProvider provider = SocialProvider.fromId(token.getAuthorizedClientRegistrationId()).orElseThrow();
            User user = socialLoginService.login(SocialProfile.from(provider, token.getPrincipal().getAttributes()));
            // # 뒤(fragment)는 브라우저가 서버로 보내지 않아서 서버·프록시 로그에 코드가 남지 않아요
            response.sendRedirect(url("/oauth/callback#code=" + codeStore.issue(user.getId())));
        } catch (SocialLoginException e) {
            response.sendRedirect(url("/login?oauthError=" + e.getReason()));
        } catch (RuntimeException e) {
            log.warn("소셜 로그인 처리 실패", e);
            response.sendRedirect(url("/login?oauthError=failed"));
        }
    }

    /** 사용자가 동의 화면에서 취소했거나, state가 맞지 않거나, 토큰을 받지 못했을 때 */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        endSession(request);
        log.info("소셜 로그인 실패: {}", exception.getMessage());
        // 동의 화면에서 '취소'를 누른 건 오류가 아니라 사용자의 선택이라, 화면에서 다른 문장으로 안내해요
        boolean cancelled = exception instanceof OAuth2AuthenticationException oauth
                && "access_denied".equals(oauth.getError().getErrorCode());
        response.sendRedirect(url("/login?oauthError=" + (cancelled ? "cancelled" : "failed")));
    }

    /** 세션은 로그인하는 몇 초 동안 state(위조 방지 값)를 기억하는 데만 써요. 끝나면 바로 지워요 */
    private static void endSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    private String url(String path) {
        String base = properties.publicUrl();
        return base == null || base.isBlank() ? path : StringUtils.trimTrailingCharacter(base, '/') + path;
    }
}
