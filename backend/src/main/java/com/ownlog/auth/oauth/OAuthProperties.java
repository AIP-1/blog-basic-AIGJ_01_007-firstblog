package com.ownlog.auth.oauth;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 소셜 로그인 설정 (application.yaml의 ownlog.oauth). 키는 환경변수로만 받아요 (코드·커밋에 넣지 않아요).
 *
 * @param clients   kakao·naver·google·github → 그 서비스에서 발급받은 client id/secret. 비어 있으면 그 서비스는 꺼져요
 * @param publicUrl 서비스 주소 (예: https://ownlog.java21.net). 비우면 요청 주소로 계산해요 (로컬 개발)
 */
@ConfigurationProperties("ownlog.oauth")
public record OAuthProperties(Map<String, Client> clients, String publicUrl) {

    public OAuthProperties {
        clients = clients == null ? Map.of() : Map.copyOf(clients);
    }

    public record Client(String clientId, String clientSecret) {

        boolean isConfigured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }
}
