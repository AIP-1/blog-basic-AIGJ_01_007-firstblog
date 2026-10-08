package com.ownlog.global.config;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final SecurityErrorHandler securityErrorHandler;

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
                        .requestMatchers(HttpMethod.GET, "/api/auth/check-username").permitAll()
                        // 🔓 읽기 전용 공개 API (5.1, 5.3, 6.1 ~ 6.5, 6.9, 8.1)
                        .requestMatchers(HttpMethod.GET,
                                "/api/blogs/**",
                                "/api/posts", "/api/posts/search", "/api/posts/*/comments",
                                "/api/tags/*/posts",
                                "/api/topics",
                                "/uploads/**").permitAll()
                        // 예외가 나면 Spring이 /error로 보내요. 막혀 있으면 진짜 에러가 401로 가려져요
                        .requestMatchers("/error").permitAll()
                        // 🔒 나머지는 모두 로그인 필요 (👤 주인 확인은 서비스에서)
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider, userRepository),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** 비밀번호 단방향 해시 (NF-01) */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
