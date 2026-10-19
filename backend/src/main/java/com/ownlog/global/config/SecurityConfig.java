package com.ownlog.global.config;

import com.ownlog.auth.oauth.JdbcOAuth2AuthorizationRequestRepository;
import com.ownlog.auth.oauth.OAuthClientRegistrations;
import com.ownlog.auth.oauth.OAuthLoginHandlers;
import com.ownlog.auth.oauth.OAuthProperties;
import com.ownlog.global.security.JwtAuthenticationFilter;
import com.ownlog.global.security.JwtProperties;
import com.ownlog.global.security.JwtTokenProvider;
import com.ownlog.global.security.SecurityErrorHandler;
import com.ownlog.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, OAuthProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final SecurityErrorHandler securityErrorHandler;
    private final OAuthClientRegistrations oauthClientRegistrations;
    private final OAuthLoginHandlers oauthLoginHandlers;
    private final JdbcOAuth2AuthorizationRequestRepository oauthAuthorizationRequestRepository;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 토큰을 쿠키가 아니라 Authorization 헤더로 보내서, 브라우저가 자동으로 실어 보내는
                // 일이 없어요 → CSRF 공격이 성립하지 않아서 꺼요
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)   // 로그아웃은 프론트엔드가 토큰을 지우는 방식 (U-03)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .authorizeHttpRequests(auth -> auth
                        // 🔓 인증 (API 명세서 4.1 ~ 4.3)
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/check-username", "/api/auth/oauth/providers").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/oauth/exchange").permitAll()
                        // 🔓 읽기 전용 공개 API (5.1, 5.3, 6.1 ~ 6.5, 6.9, 8.1, 12.1 ~ 12.2, 13.1, 14.1 ~ 14.3)
                        .requestMatchers(HttpMethod.GET,
                                "/api/blogs/**",
                                "/api/posts", "/api/posts/search", "/api/posts/*/comments", "/api/posts/*/backlinks",
                                "/api/tags/*/posts",
                                "/api/topics",
                                "/api/time-capsules/opened",
                                "/api/universe",
                                "/uploads/**").permitAll()
                        // 예외가 나면 Spring이 /error로 보내요. 막혀 있으면 진짜 에러가 401로 가려져요
                        .requestMatchers("/error").permitAll()
                        // 🔒 나머지 API는 모두 로그인 필요 (👤 주인 확인은 서비스에서).
                        // 경로 패턴은 MVC와 같은 방식으로 디코딩해서 비교해요. getRequestURI()로 직접 비교하면
                        // "/%61pi/images"처럼 인코딩한 주소가 "/api/"로 시작하지 않는 것처럼 보여 인증을 건너뛰어요
                        .requestMatchers("/api/**").authenticated()
                        // API가 아닌 주소는 React 화면(index.html, /assets/**, /@아이디 같은 화면 주소)이라 누구나 받을 수 있어요.
                        // 화면 안에서 필요한 권한은 각 API가 다시 확인해요
                        .anyRequest().permitAll())
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider, userRepository),
                        UsernamePasswordAuthenticationFilter.class);

        // 소셜 로그인 (U-09): 키를 등록한 서비스가 하나라도 있을 때만 켜요.
        //   /oauth2/authorization/kakao → 카카오 동의 화면 → /login/oauth2/code/kakao → OAuthLoginHandlers
        // 동의 화면에 다녀오는 동안의 요청 정보(state)는 서버 메모리 세션이 아니라 DB + 쿠키에 둬요.
        // 블루-그린 전환 중 다른 서버로 돌아와도 이어지게 하려고요 (JdbcOAuth2AuthorizationRequestRepository, 트러블슈팅 24번)
        if (!oauthClientRegistrations.all().isEmpty()) {
            http.oauth2Login(oauth -> oauth
                    .clientRegistrationRepository(new InMemoryClientRegistrationRepository(oauthClientRegistrations.all()))
                    .authorizationEndpoint(endpoint -> endpoint.authorizationRequestRepository(oauthAuthorizationRequestRepository))
                    .loginPage("/login")   // Spring 기본 로그인 화면을 만들지 않고 React 화면(/login)을 써요
                    .successHandler(oauthLoginHandlers)
                    .failureHandler(oauthLoginHandlers));
        }

        return http.build();
    }

    /**
     * 비밀번호 단방향 해시 (NF-01).
     * static: 이 설정 클래스가 만들어지기 전에도 꺼낼 수 있어요. 소셜 로그인 처리기 → 회원 가입 서비스 → PasswordEncoder를
     * 이 클래스가 다시 필요로 해서, static이 아니면 "서로를 기다리는" 순환 참조로 서버가 뜨지 않아요.
     */
    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
