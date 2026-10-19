package com.ownlog.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.ownlog.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * 블루-그린 전환 중에도 소셜 로그인이 이어지는지 (트러블슈팅 24번).
 * "다른 서버" = 같은 DB를 쓰는 새 객체로 흉내 내요. 예전처럼 서버 메모리에 있었다면 새 객체는 아무것도 몰라요.
 */
class OAuthSharedStateTest extends IntegrationTest {

    @Autowired JdbcOAuth2AuthorizationRequestRepository blue;
    @Autowired OAuthLoginCodeStore blueCodes;

    private JdbcOAuth2AuthorizationRequestRepository green() {
        return new JdbcOAuth2AuthorizationRequestRepository(jdbcTemplate);
    }

    private static OAuth2AuthorizationRequest request(String state) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .clientId("kakao-test-id")
                .redirectUri("https://ownlog.example.com/login/oauth2/code/kakao")
                .state(state)
                .build();
    }

    /** blue에서 동의 화면으로 보내고, 응답의 Set-Cookie를 돌려줘요 */
    private Cookie startAtBlue(String state) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        blue.saveAuthorizationRequest(request(state), new MockHttpServletRequest(), response);
        return response.getCookie(JdbcOAuth2AuthorizationRequestRepository.COOKIE);
    }

    private static MockHttpServletRequest callback(String state, Cookie... cookies) {
        MockHttpServletRequest callback = new MockHttpServletRequest("GET", "/login/oauth2/code/kakao");
        callback.setParameter("state", state);
        if (cookies.length > 0) {
            callback.setCookies(cookies);
        }
        return callback;
    }

    @Test
    @DisplayName("1. blue에서 시작한 로그인이 green으로 돌아와도 이어지고, 한 번 쓰면 사라진다")
    void continuesOnAnotherServer() {
        Cookie cookie = startAtBlue("state-1");
        assertThat(cookie.getValue()).isEqualTo("state-1");
        assertThat(cookie.isHttpOnly()).isTrue();

        OAuth2AuthorizationRequest found = green().removeAuthorizationRequest(callback("state-1", cookie), new MockHttpServletResponse());

        assertThat(found).isNotNull();
        assertThat(found.getState()).isEqualTo("state-1");
        assertThat(found.getClientId()).isEqualTo("kakao-test-id");
        assertThat(green().removeAuthorizationRequest(callback("state-1", cookie), new MockHttpServletResponse())).isNull();
    }

    @Test
    @DisplayName("2. 시작한 브라우저의 쿠키가 없거나 다르면 거부 (로그인 CSRF: 남이 만든 state를 보내도 안 통한다)")
    void rejectsOtherBrowser() {
        startAtBlue("attacker-state");

        assertThat(green().loadAuthorizationRequest(callback("attacker-state"))).isNull();
        assertThat(green().loadAuthorizationRequest(
                callback("attacker-state", new Cookie(JdbcOAuth2AuthorizationRequestRepository.COOKIE, "victim-state")))).isNull();
    }

    @Test
    @DisplayName("3. 5분이 지난 요청은 거부")
    void rejectsExpired() {
        Cookie cookie = startAtBlue("old-state");
        jdbcTemplate.update("UPDATE oauth_authorization_requests SET expires_at = now() - interval '1 second'");

        assertThat(green().loadAuthorizationRequest(callback("old-state", cookie))).isNull();
    }

    @Test
    @DisplayName("4. blue가 준 1회용 코드를 green이 교환할 수 있고, 두 번은 안 된다. DB에는 해시만 있다")
    void codeWorksAcrossServers() throws Exception {
        TestUser nayoung = signupAndLogin("nayoung");
        String code = blueCodes.issue(nayoung.userId());

        OAuthLoginCodeStore greenCodes = new OAuthLoginCodeStore(jdbcTemplate);
        assertThat(greenCodes.consume(code)).contains(nayoung.userId());
        assertThat(greenCodes.consume(code)).isEmpty();

        String another = blueCodes.issue(nayoung.userId());
        String stored = jdbcTemplate.queryForObject("SELECT code_hash FROM oauth_login_codes", String.class);
        assertThat(stored).hasSize(64).isNotEqualTo(another);
    }

    @Test
    @DisplayName("5. 같은 코드로 동시에 두 번 교환해도 한 번만 성공")
    void codeConsumedOnceUnderConcurrency() throws Exception {
        TestUser nayoung = signupAndLogin("nayoung");
        String code = blueCodes.issue(nayoung.userId());
        List<Optional<Long>> results = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        for (int i = 0; i < 2; i++) {
            pool.submit(() -> {
                go.await();
                results.add(new OAuthLoginCodeStore(jdbcTemplate).consume(code));
                return null;
            });
        }
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(results).hasSize(2);
        assertThat(results.stream().filter(Optional::isPresent).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("6. 60초가 지난 코드는 교환 불가")
    void expiredCodeRejected() throws Exception {
        TestUser nayoung = signupAndLogin("nayoung");
        String code = blueCodes.issue(nayoung.userId());
        jdbcTemplate.update("UPDATE oauth_login_codes SET expires_at = now() - interval '1 second'");

        assertThat(blueCodes.consume(code)).isEmpty();
    }
}
