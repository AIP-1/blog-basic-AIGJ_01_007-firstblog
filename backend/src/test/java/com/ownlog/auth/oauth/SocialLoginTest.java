package com.ownlog.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ownlog.IntegrationTest;
import com.ownlog.user.domain.User;
import com.ownlog.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.web.servlet.MockMvc;

/** U-09 소셜 로그인: 회원 찾기·만들기, 1회용 코드 교환, 비밀번호 없는 회원의 설정·탈퇴 */
class SocialLoginTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired SocialLoginService socialLoginService;
    @Autowired OAuthLoginHandlers handlers;
    @Autowired UserRepository userRepository;

    private static SocialProfile kakao(String id, String nickname) {
        return SocialProfile.from(SocialProvider.KAKAO, Map.of("id", Long.parseLong(id),
                "kakao_account", Map.of("profile", Map.of("nickname", nickname))));
    }

    /** 서비스에서 돌아온 직후 Spring이 만들어 주는 인증 정보를 흉내 내서 성공 처리기를 불러요 */
    private MockHttpServletResponse finishOAuth(String registrationId, Map<String, Object> attributes, String nameKey)
            throws Exception {
        var authorities = List.of(new SimpleGrantedAuthority("OAUTH2_USER"));
        var token = new OAuth2AuthenticationToken(new DefaultOAuth2User(authorities, attributes, nameKey),
                authorities, registrationId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        handlers.onAuthenticationSuccess(new MockHttpServletRequest(), response, token);
        return response;
    }

    private String exchange(String code) throws Exception {
        return mockMvc.perform(post("/api/auth/oauth/exchange").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\"}".formatted(code)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("1. 처음 온 카카오 계정 → 회원·블로그·미분류를 만들고, 비밀번호는 정하지 않은 상태")
    void firstLoginCreatesUser() {
        User user = socialLoginService.login(kakao("1234567", "카카오나영"));

        assertThat(user.getUsername()).matches("kakao_[a-z0-9]{6}");
        assertThat(user.getNickname()).isEqualTo("카카오나영");
        assertThat(user.isPasswordSet()).isFalse();
        assertThat(user.getEmail()).isEqualTo("kakao_1234567@social.ownlog.invalid");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM categories c JOIN blogs b ON b.id = c.blog_id WHERE b.user_id = ? AND c.is_default",
                Integer.class, user.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("2. 같은 소셜 계정으로 다시 오면 같은 회원으로 로그인 (닉네임이 바뀌어도)")
    void secondLoginReturnsSameUser() {
        User first = socialLoginService.login(kakao("42", "처음"));
        User again = socialLoginService.login(kakao("42", "바뀐 닉네임"));

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("3. 깃허브 로그인 이름이 아이디 규칙에 맞으면 그대로 써요 (Nay-Oung → nay_oung)")
    void githubLoginBecomesUsername() {
        User user = socialLoginService.login(SocialProfile.from(SocialProvider.GITHUB,
                Map.of("id", 99, "login", "Nay-Oung", "name", "나영")));

        assertThat(user.getUsername()).isEqualTo("nay_oung");
        assertThat(user.getNickname()).isEqualTo("나영");
    }

    @Test
    @DisplayName("4. 확인된 이메일이 기존 회원과 같으면 자동 연결하지 않고 막아요 (계정 탈취 방지)")
    void emailInUseIsRejected() throws Exception {
        signupAndLogin("nayoung");   // nayoung@test.com

        SocialProfile google = SocialProfile.from(SocialProvider.GOOGLE,
                Map.of("sub", "g-1", "email", "NAYOUNG@test.com", "email_verified", true, "name", "나영"));
        assertThatThrownBy(() -> socialLoginService.login(google))
                .isInstanceOf(SocialLoginException.class)
                .extracting("reason").isEqualTo("email_in_use");

        // 확인되지 않은 이메일은 아예 쓰지 않아서, 남의 이메일을 적어 와도 그 계정에 닿지 않아요
        SocialProfile unverified = SocialProfile.from(SocialProvider.GOOGLE,
                Map.of("sub", "g-2", "email", "nayoung@test.com", "email_verified", false));
        assertThat(socialLoginService.login(unverified).getEmail()).endsWith("@social.ownlog.invalid");
    }

    @Test
    @DisplayName("5. 네이버는 response 안의 값을 읽고, 긴 닉네임은 20자로 잘라요")
    void naverProfile() {
        String longName = "가".repeat(25);
        User user = socialLoginService.login(SocialProfile.from(SocialProvider.NAVER,
                Map.of("resultcode", "00", "response", Map.of("id", "nv-abc", "nickname", longName, "email", "n@naver.com"))));

        assertThat(user.getNickname()).hasSize(20);
        assertThat(user.getEmail()).isEqualTo("n@naver.com");
    }

    @Test
    @DisplayName("6. 성공 처리 → /oauth/callback#code=… → 코드 교환으로 토큰. 코드는 한 번만 쓸 수 있어요")
    void successRedirectsWithOneTimeCode() throws Exception {
        MockHttpServletResponse response = finishOAuth("kakao",
                Map.of("id", 777L, "properties", Map.of("nickname", "칠칠칠")), "id");

        String location = response.getRedirectedUrl();
        assertThat(location).startsWith("/oauth/callback#code=");
        String code = location.substring(location.indexOf("code=") + 5);

        String body = exchange(code);
        assertThat((String) JsonPath.read(body, "$.accessToken")).isNotBlank();
        assertThat((String) JsonPath.read(body, "$.user.nickname")).isEqualTo("칠칠칠");

        mockMvc.perform(post("/api/auth/oauth/exchange").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\"}".formatted(code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OAUTH_CODE"));
    }

    @Test
    @DisplayName("7. 이메일이 겹치면 로그인 화면으로 이유를 들고 돌아가요")
    void emailInUseRedirectsToLogin() throws Exception {
        signupAndLogin("nayoung");
        MockHttpServletResponse response = finishOAuth("google",
                Map.of("sub", "g-9", "email", "nayoung@test.com", "email_verified", true), "sub");

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?oauthError=email_in_use");
    }

    @Test
    @DisplayName("8. 비밀번호가 없는 소셜 회원: 현재 비밀번호 없이 처음 비밀번호를 정하고, 그 뒤엔 이메일로도 로그인")
    void socialUserSetsFirstPassword() throws Exception {
        String token = JsonPath.read(exchange(codeFor(kakao("8", "팔"))), "$.accessToken");

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.passwordSet").value(false));
        mockMvc.perform(patch("/api/users/me/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"ownlog1234\",\"newPasswordConfirm\":\"ownlog1234\"}"))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.passwordSet").value(true));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"kakao_8@social.ownlog.invalid\",\"password\":\"ownlog1234\"}"))
                .andExpect(status().isOk());

        // 이제 비밀번호가 있으니 바꿀 때는 현재 비밀번호가 필요해요
        mockMvc.perform(patch("/api/users/me/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"ownlog5678\",\"newPasswordConfirm\":\"ownlog5678\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));
    }

    @Test
    @DisplayName("9. 소셜 회원 탈퇴는 아이디로 확인하고, 연결이 끊겨서 다시 오면 새 회원이 돼요")
    void socialUserWithdraw() throws Exception {
        User user = socialLoginService.login(kakao("9", "구"));
        String token = JsonPath.read(exchange(codeFor(kakao("9", "구"))), "$.accessToken");

        mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"someone_else\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WRONG_CONFIRM_USERNAME"));
        mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\"}".formatted(user.getUsername())))
                .andExpect(status().isNoContent());

        User again = socialLoginService.login(kakao("9", "구"));
        assertThat(again.getId()).isNotEqualTo(user.getId());
    }

    @Test
    @DisplayName("10. 키를 하나도 등록하지 않으면 소셜 로그인 목록이 비어 있어요 (버튼이 안 보여요)")
    void noProvidersByDefault() throws Exception {
        mockMvc.perform(get("/api/auth/oauth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers").isEmpty());
    }

    @Test
    @DisplayName("11. 동의 화면에서 취소(access_denied)하면 '취소했어요' 안내, 그 밖의 실패는 'failed'")
    void cancelledIsDistinguished() throws Exception {
        MockHttpServletResponse cancelled = new MockHttpServletResponse();
        handlers.onAuthenticationFailure(new MockHttpServletRequest(), cancelled,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied")));
        assertThat(cancelled.getRedirectedUrl()).isEqualTo("/login?oauthError=cancelled");

        MockHttpServletResponse failed = new MockHttpServletResponse();
        handlers.onAuthenticationFailure(new MockHttpServletRequest(), failed,
                new OAuth2AuthenticationException(new OAuth2Error("authorization_request_not_found")));
        assertThat(failed.getRedirectedUrl()).isEqualTo("/login?oauthError=failed");
    }

    @Test
    @DisplayName("12. 같은 소셜 계정의 첫 로그인이 동시에 두 번 와도 회원은 1명 (UNIQUE 제약이 막아요)")
    void concurrentFirstLoginCreatesOneUser() throws Exception {
        int threads = 2;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    socialLoginService.login(kakao("5555", "동시"));
                } catch (Throwable t) {
                    errors.add(t);   // 늦은 쪽은 UNIQUE 위반으로 실패하고 트랜잭션 전체가 롤백돼요
                }
            });
        }
        ready.await(5, TimeUnit.SECONDS);
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_social_accounts WHERE provider = 'KAKAO' AND provider_user_id = '5555'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE email = 'kakao_5555@social.ownlog.invalid'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM blogs", Integer.class)).isEqualTo(1);   // 실패한 쪽의 블로그도 롤백
        assertThat(errors).hasSizeLessThanOrEqualTo(1);
    }

    private String codeFor(SocialProfile profile) throws Exception {
        MockHttpServletResponse response = finishOAuth("kakao",
                Map.of("id", Long.parseLong(profile.providerUserId()),
                        "properties", Map.of("nickname", profile.nickname())), "id");
        String location = response.getRedirectedUrl();
        return location.substring(location.indexOf("code=") + 5);
    }
}
