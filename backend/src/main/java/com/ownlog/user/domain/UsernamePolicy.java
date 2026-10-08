package com.ownlog.user.domain;

import java.util.Set;
import java.util.regex.Pattern;

/** 아이디 규칙 (U-01): 영문 소문자, 숫자, _ 3~20자 + 예약어 금지. 가입 검증과 아이디 확인 API가 함께 써요. */
public final class UsernamePolicy {

    public static final String REGEX = "^[a-z0-9_]{3,20}$";
    private static final Pattern PATTERN = Pattern.compile(REGEX);

    /** 운영자 사칭이나 시스템 이름으로 오해할 수 있는 아이디 */
    private static final Set<String> RESERVED = Set.of(
            "admin", "administrator", "root", "system", "ownlog", "official",
            "support", "help", "manager", "staff", "api", "null", "undefined");

    private UsernamePolicy() {
    }

    public static boolean isValidFormat(String username) {
        return username != null && PATTERN.matcher(username).matches();
    }

    public static boolean isReserved(String username) {
        return RESERVED.contains(username);
    }
}
