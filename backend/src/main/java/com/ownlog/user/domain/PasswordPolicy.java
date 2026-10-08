package com.ownlog.user.domain;

/** 비밀번호 규칙 (8장 입력 규칙): 8~64자, 영문과 숫자 포함. 가입과 비밀번호 변경이 함께 써요 */
public final class PasswordPolicy {

    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$";
    public static final String MESSAGE = "비밀번호는 8~64자이고 영문과 숫자를 포함해야 해요.";

    private PasswordPolicy() {
    }
}
