package com.ownlog.user.domain;

/** 비밀번호 규칙 (8장 입력 규칙): 8~64자, 영문과 숫자 포함. 가입과 비밀번호 변경이 함께 써요 */
public final class PasswordPolicy {

    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$";
    public static final String MESSAGE = "비밀번호는 8~64자이고 영문과 숫자를 포함해야 해요.";

    /** BCrypt가 받을 수 있는 최대 길이. 한글은 한 글자에 3바이트라 글자 수 규칙만으로는 넘을 수 있어요 */
    public static final int MAX_BYTES = 72;
    public static final String BYTES_MESSAGE = "비밀번호가 너무 길어요. 한글은 한 글자를 3자로 세서 72자까지 쓸 수 있어요.";

    private PasswordPolicy() {
    }
}
