package com.ownlog.global.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yaml의 ownlog.jwt.* 설정 (secret, access-token-validity: 2h) */
@ConfigurationProperties(prefix = "ownlog.jwt")
public record JwtProperties(String secret, Duration accessTokenValidity) {
}
