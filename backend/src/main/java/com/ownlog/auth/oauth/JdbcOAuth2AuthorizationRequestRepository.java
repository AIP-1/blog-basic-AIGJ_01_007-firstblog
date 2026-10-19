package com.ownlog.auth.oauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

/**
 * 소셜 로그인 "동의 화면 다녀오기" 동안의 요청 정보(state 포함)를 DB에 둬요.
 *
 * Spring 기본값은 서버 메모리 세션이라, 블루-그린 전환 중에 blue에서 시작하고 green으로 돌아오면
 * green이 그 요청을 몰라서 로그인이 실패했어요 (authorization_request_not_found, 트러블슈팅 24번).
 * blue와 green이 함께 쓰는 DB에 두면 어느 서버로 돌아와도 이어져요.
 *
 * <p>state를 DB에서만 찾으면 안 돼요. 그러면 공격자가 자기 브라우저에서 시작한 state를 피해자에게 보내
 * 피해자를 공격자 계정으로 로그인시킬 수 있어요(로그인 CSRF). 그래서 <b>시작한 브라우저에 같은 state를 쿠키로</b> 주고,
 * 돌아올 때 주소의 state와 쿠키가 같을 때만 받아들여요. 쿠키는 우리 도메인 것이라 공격자가 피해자 브라우저에 넣을 수 없어요.
 */
@Component
@RequiredArgsConstructor
public class JdbcOAuth2AuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String COOKIE = "OWNLOG_OAUTH_STATE";
    static final Duration TTL = Duration.ofMinutes(5);

    /** DB에서 꺼낼 때 Spring Security와 Java 기본 클래스만 되살려요 (예상하지 못한 클래스는 거부) */
    private static final ObjectInputFilter ALLOWED =
            ObjectInputFilter.Config.createFilter("maxdepth=20;java.**;org.springframework.security.**;!*");

    private final JdbcTemplate jdbcTemplate;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = matchingState(request);
        if (state == null) {
            return null;
        }
        List<byte[]> rows = jdbcTemplate.query(
                "SELECT request FROM oauth_authorization_requests WHERE state = ? AND expires_at > now()",
                (rs, i) -> rs.getBytes(1), state);
        return rows.isEmpty() ? null : deserialize(rows.get(0));
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            removeAuthorizationRequest(request, response);
            return;
        }
        jdbcTemplate.update("DELETE FROM oauth_authorization_requests WHERE expires_at <= now()");   // 다녀오지 않은 요청 정리
        jdbcTemplate.update("INSERT INTO oauth_authorization_requests (state, request, expires_at) VALUES (?, ?, now() + ?::interval)",
                authorizationRequest.getState(), serialize(authorizationRequest), TTL.toSeconds() + " seconds");
        writeCookie(request, response, authorizationRequest.getState(), TTL);
    }

    /** 돌아왔을 때 한 번 꺼내고 지워요 (같은 state로 두 번 로그인할 수 없게) */
    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        String state = matchingState(request);
        writeCookie(request, response, "", Duration.ZERO);   // 쿠키는 결과와 상관없이 지워요
        if (state == null) {
            return null;
        }
        List<byte[]> rows = jdbcTemplate.query(
                "DELETE FROM oauth_authorization_requests WHERE state = ? AND expires_at > now() RETURNING request",
                (rs, i) -> rs.getBytes(1), state);
        return rows.isEmpty() ? null : deserialize(rows.get(0));
    }

    /** 주소의 state와 쿠키의 state가 같을 때만 그 값을, 아니면 null */
    private static String matchingState(HttpServletRequest request) {
        String state = request.getParameter("state");
        if (state == null || state.isBlank() || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE.equals(cookie.getName()) && state.equals(cookie.getValue())) {
                return state;
            }
        }
        return null;
    }

    private static void writeCookie(HttpServletRequest request, HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE, value)
                .httpOnly(true)               // 화면 스크립트가 읽을 필요 없어요
                .secure(request.isSecure())   // 운영(https)에서는 https로만 보내요
                .sameSite("Lax")              // 카카오에서 돌아오는 주소 이동(GET)에는 실려 와요
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static byte[] serialize(OAuth2AuthorizationRequest authorizationRequest) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(authorizationRequest);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static OAuth2AuthorizationRequest deserialize(byte[] data) {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data))) {
            in.setObjectInputFilter(ALLOWED);
            return (OAuth2AuthorizationRequest) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            return null;   // 버전이 달라 읽을 수 없으면 "요청 없음"으로 처리 → 사용자는 다시 시도
        }
    }
}
