package com.ownlog.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 문자열을 UTF-8로 바꿨을 때 max바이트 이하인가. 글자 수(@Size)와 달리 한글은 한 글자에 3바이트로 세요.
 * BCrypt는 72바이트를 넘으면 예외를 던져서, 비밀번호 칸에 이걸 붙여 500 대신 400으로 알려줘요.
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
public @interface MaxUtf8Bytes {

    int value();

    String message();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
