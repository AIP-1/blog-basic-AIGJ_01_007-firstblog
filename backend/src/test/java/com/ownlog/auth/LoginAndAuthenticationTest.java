package com.ownlog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ownlog.IntegrationTest;
import com.ownlog.user.domain.User;
import com.ownlog.user.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** API 4.2 로그인, 4.4 내 정보, 그리고 JWT 인증 동작 (B단계 SecurityConfig, JwtAuthenticationFilter) */
class LoginAndAuthenticationTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Value("${ownlog.jwt.secret}")
    String jwtSecret;

    @BeforeEach
    void signupNayoung() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"nayoung@gmail.com","password":"ownlog1234","passwordConfirm":"ownlog1234",
                         "username":"nayoung","nickname":"나영"}
                        """))
                .andExpect(status().isCreated());
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    private String loginAndGetToken() throws Exception {
        String body = login("nayoung@gmail.com", "ownlog1234").andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private ResultActions getMe(String token) throws Exception {
        return mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    @DisplayName("1. 정상 로그인: Bearer 토큰, 유효 시간 7200초, 내 블로그 id를 돌려준다")
    void loginSuccess() throws Exception {
        login("nayoung@gmail.com", "ownlog1234")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(not(emptyString())))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(7200))
                .andExpect(jsonPath("$.user.username").value("nayoung"))
                .andExpect(jsonPath("$.user.blogId").isNumber());
    }

    @Test
    @DisplayName("2. 대문자 이메일로도 로그인된다 (소문자로 바꿔 비교)")
    void loginWithUppercaseEmail() throws Exception {
        login("NAYOUNG@Gmail.com", "ownlog1234").andExpect(status().isOk());
    }

    @Test
    @DisplayName("3. 틀린 비밀번호와 없는 이메일은 똑같은 401 응답이다 (가입 여부를 알려주지 않음)")
    void loginFailureIsIndistinguishable() throws Exception {
        String wrongPassword = login("nayoung@gmail.com", "wrong1234")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = login("nobody@gmail.com", "ownlog1234")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownEmail).isEqualTo(wrongPassword);
    }

    @Test
    @DisplayName("4. 토큰 없이 🔒 주소에 오면 401 UNAUTHORIZED JSON")
    void protectedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    @DisplayName("5. 토큰으로 내 정보를 보고, 시간은 +09:00 형식이다")
    void getMeWithToken() throws Exception {
        getMe(loginAndGetToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("nayoung@gmail.com"))
                .andExpect(jsonPath("$.username").value("nayoung"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.blog.name").value("나영의 블로그"))
                .andExpect(jsonPath("$.createdAt").value(matchesPattern(".*\\+09:00$")));
    }

    @Test
    @DisplayName("6. 다른 비밀키로 서명한 위조 토큰은 401")
    void forgedToken() throws Exception {
        String forged = Jwts.builder()
                .subject("1")
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor("another-secret-key-made-by-attacker-32bytes".getBytes(StandardCharsets.UTF_8)))
                .compact();

        getMe(forged).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("7. 만료된 토큰은 401")
    void expiredToken() throws Exception {
        Long userId = userRepository.findByEmail("nayoung@gmail.com").orElseThrow().getId();
        String expired = Jwts.builder()
                .subject(String.valueOf(userId))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        getMe(expired).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("8. 잘못된 토큰이어도 🔓 주소는 방문자로 통과한다 (API 명세서 1.2)")
    void publicWithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/auth/check-username").param("username", "newuser")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer this.is.not-a-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    @DisplayName("9. 탈퇴한 회원의 토큰은 만료 전이라도 401 (U-03)")
    void withdrawnUserToken() throws Exception {
        String token = loginAndGetToken();
        getMe(token).andExpect(status().isOk());

        User user = userRepository.findByEmail("nayoung@gmail.com").orElseThrow();
        user.withdraw(passwordEncoder.encode("random"));
        userRepository.save(user);

        getMe(token).andExpect(status().isUnauthorized());
    }
}
