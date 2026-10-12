package com.ownlog.global.util;

import java.util.Locale;

/** LIKE 검색어: 소문자로 바꾸고 %, _, \는 글자 그대로 찾도록 이스케이프해서 '%...%'로 감싸요 (쿼리에서 escape '\' 사용) */
public final class LikePattern {

    private LikePattern() {
    }

    public static String contains(String keyword) {
        String escaped = keyword.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
