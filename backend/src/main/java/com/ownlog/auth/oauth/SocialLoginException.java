package com.ownlog.auth.oauth;

/** 소셜 로그인을 끝낼 수 없을 때. reason은 로그인 화면 주소(?oauthError=)로 넘겨서 안내 문구를 골라요 */
public class SocialLoginException extends RuntimeException {

    private final String reason;

    public SocialLoginException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
