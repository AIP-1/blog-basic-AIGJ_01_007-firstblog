package com.ownlog.global.error;

import lombok.Getter;

/**
 * 서비스 규칙 위반을 알리는 예외. 예: throw new BusinessException(ErrorCode.ALREADY_REPORTED);
 * RuntimeException이라 @Transactional 안에서 던지면 자동으로 롤백돼요.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
