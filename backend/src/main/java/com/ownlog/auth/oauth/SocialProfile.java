package com.ownlog.auth.oauth;

import java.util.Map;

/**
 * 서비스마다 다른 모양의 회원 정보를 하나로 맞춘 것.
 *
 * @param providerUserId 그 서비스의 회원 번호 (바뀌지 않는 값)
 * @param email          확인된 이메일 (없거나 확인되지 않았으면 null)
 * @param nickname       표시 이름 (없으면 null)
 * @param usernameHint   아이디 후보 (깃허브 로그인 이름 등, 없으면 null)
 */
public record SocialProfile(SocialProvider provider, String providerUserId, String email, String nickname,
                            String usernameHint) {

    private static final String EMAIL_KEY = "email";
    private static final String NICKNAME_KEY = "nickname";

    /** 서비스별 응답 → SocialProfile. 회원 번호가 없으면 IllegalArgumentException */
    public static SocialProfile from(SocialProvider provider, Map<String, Object> attributes) {
        return switch (provider) {
            case KAKAO -> {
                Map<String, Object> account = map(attributes.get("kakao_account"));
                Map<String, Object> profile = map(account.get("profile"));
                Map<String, Object> properties = map(attributes.get("properties"));
                String email = Boolean.TRUE.equals(account.get("is_email_verified")) ? str(account.get(EMAIL_KEY)) : null;
                String nickname = firstNonBlank(str(profile.get(NICKNAME_KEY)), str(properties.get(NICKNAME_KEY)));
                yield of(provider, attributes.get("id"), email, nickname, null);
            }
            case NAVER -> {
                Map<String, Object> response = map(attributes.get("response"));
                // 네이버 이메일은 네이버가 확인한 연락처 이메일이에요
                yield of(provider, response.get("id"), str(response.get(EMAIL_KEY)),
                        firstNonBlank(str(response.get(NICKNAME_KEY)), str(response.get("name"))), null);
            }
            case GOOGLE -> of(provider, attributes.get("sub"),
                    Boolean.TRUE.equals(attributes.get("email_verified")) ? str(attributes.get(EMAIL_KEY)) : null,
                    str(attributes.get("name")), null);
            // 깃허브는 공개 이메일만 와요. 확인 여부를 알 수 없어서 쓰지 않아요
            case GITHUB -> of(provider, attributes.get("id"), null,
                    firstNonBlank(str(attributes.get("name")), str(attributes.get("login"))), str(attributes.get("login")));
        };
    }

    private static SocialProfile of(SocialProvider provider, Object id, String email, String nickname, String hint) {
        String providerUserId = id == null ? null : String.valueOf(id);
        if (providerUserId == null || providerUserId.isBlank()) {
            throw new IllegalArgumentException(provider + " 응답에 회원 번호가 없어요");
        }
        return new SocialProfile(provider, providerUserId, blankToNull(email), blankToNull(nickname), blankToNull(hint));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
