package com.ownlog.global.common;

/** 글과 카테고리의 공개 범위 (ERD 3.3, 3.4) */
public enum Visibility {
    PUBLIC, PRIVATE;

    /** 요청에서 생략하면 공개예요 (P-02, B-05) */
    public static Visibility orPublic(Visibility visibility) {
        return visibility != null ? visibility : PUBLIC;
    }
}
