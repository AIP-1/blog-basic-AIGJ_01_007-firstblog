package com.ownlog.auth.oauth;

import java.util.Locale;
import java.util.Optional;

/** 소셜 로그인 서비스 4종. 주소의 registrationId(kakao 등)와 DB의 provider(KAKAO 등)를 이어 줘요 */
public enum SocialProvider {
    KAKAO, NAVER, GOOGLE, GITHUB;

    /** 주소·설정에 쓰는 이름 (kakao) */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<SocialProvider> fromId(String id) {
        for (SocialProvider p : values()) {
            if (p.id().equals(id)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
