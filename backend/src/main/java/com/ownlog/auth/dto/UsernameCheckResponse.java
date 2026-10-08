package com.ownlog.auth.dto;

/** API 명세서 4.3 응답. 사용할 수 있으면 reason은 null이에요. */
public record UsernameCheckResponse(boolean available, Reason reason) {

    public enum Reason { INVALID_FORMAT, RESERVED, DUPLICATE }

    public static UsernameCheckResponse ok() {
        return new UsernameCheckResponse(true, null);
    }

    public static UsernameCheckResponse unavailable(Reason reason) {
        return new UsernameCheckResponse(false, reason);
    }
}
