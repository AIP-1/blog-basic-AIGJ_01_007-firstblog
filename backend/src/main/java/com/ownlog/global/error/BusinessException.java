package com.ownlog.global.error;

import com.ownlog.global.error.ErrorResponse.FieldError;
import java.util.List;
import lombok.Getter;

/**
 * 서비스 규칙 위반을 알리는 예외. 예: throw new BusinessException(ErrorCode.ALREADY_REPORTED);
 * RuntimeException이라 @Transactional 안에서 던지면 자동으로 롤백돼요.
 * 어느 입력칸의 문제인지 알려줘야 하면 fieldErrors를 함께 담아요 (예: 태그 개수 초과).
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient List<FieldError> fieldErrors;   // 예외는 Serializable이라 직렬화 대상에서 빼요 (S1948)

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, List.of());
    }

    public BusinessException(ErrorCode errorCode, List<FieldError> fieldErrors) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors;
    }
}
