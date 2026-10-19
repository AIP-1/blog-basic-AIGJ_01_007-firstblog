package com.ownlog.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ownlog.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

/** 키를 등록한 서비스만 켜지고, 버튼을 누르면 그 서비스의 동의 화면으로 state와 함께 보내는지 (가짜 키) */
@TestPropertySource(properties = {
        "ownlog.oauth.public-url=https://ownlog.example.com",
        "ownlog.oauth.clients.kakao.client-id=kakao-test-id",
        "ownlog.oauth.clients.kakao.client-secret=kakao-test-secret",
        "ownlog.oauth.clients.github.client-id=github-test-id",
        "ownlog.oauth.clients.github.client-secret=github-test-secret",
        "ownlog.oauth.clients.naver.client-id=naver-only-id",   // secret이 없으면 꺼져요
})
class OAuthRedirectTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("1. 키가 모두 있는 서비스만 정해진 순서로 알려줘요")
    void enabledProviders() throws Exception {
        mockMvc.perform(get("/api/auth/oauth/providers"))
                .andExpect(jsonPath("$.providers.length()").value(2))
                .andExpect(jsonPath("$.providers[0]").value("kakao"))
                .andExpect(jsonPath("$.providers[1]").value("github"));
    }

    @Test
    @DisplayName("2. /oauth2/authorization/kakao → 카카오 동의 화면 (client_id, 고정 redirect_uri, state)")
    void redirectsToKakao() throws Exception {
        String location = mockMvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        var query = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        assertThat(location).startsWith("https://kauth.kakao.com/oauth/authorize?");
        assertThat(query.getFirst("client_id")).isEqualTo("kakao-test-id");
        assertThat(query.getFirst("redirect_uri"))
                .isEqualTo("https://ownlog.example.com/login/oauth2/code/kakao");
        assertThat(query.getFirst("state")).isNotBlank();
    }

    @Test
    @DisplayName("5. 로그인 중간 상태는 세션이 아니라 DB + 쿠키 (블루-그린 전환 중에도 이어지게)")
    void stateStoredInDbAndCookieNotSession() throws Exception {
        var response = mockMvc.perform(get("/oauth2/authorization/kakao")).andReturn().getResponse();
        // 주소 안의 state는 URL 인코딩돼 있어요 (= → %3D). 서버는 디코딩된 값으로 받아요
        String state = java.net.URLDecoder.decode(UriComponentsBuilder.fromUriString(response.getRedirectedUrl()).build()
                .getQueryParams().getFirst("state"), java.nio.charset.StandardCharsets.UTF_8);

        assertThat(response.getCookie("OWNLOG_OAUTH_STATE").getValue()).isEqualTo(state);
        assertThat(response.getCookie("JSESSIONID")).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM oauth_authorization_requests WHERE state = ?",
                Integer.class, state)).isEqualTo(1);
    }

    @Test
    @DisplayName("3. 꺼진 서비스(네이버)로는 동의 화면에 가지 않아요")
    void disabledProviderDoesNotRedirect() throws Exception {
        String location = mockMvc.perform(get("/oauth2/authorization/naver"))
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(location == null || !location.contains("nid.naver.com")).isTrue();
    }

    @Test
    @DisplayName("4. state 없이 돌아오는 요청(위조)은 실패 처리 → 로그인 화면")
    void callbackWithoutStateFails() throws Exception {
        String location = mockMvc.perform(get("/login/oauth2/code/kakao").param("code", "abc").param("state", "forged"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(location).isEqualTo("https://ownlog.example.com/login?oauthError=failed");
    }
}
