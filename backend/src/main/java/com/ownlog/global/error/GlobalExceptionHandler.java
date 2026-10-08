package com.ownlog.global.error;

import com.ownlog.global.error.ErrorResponse.FieldError;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 컨트롤러에서 나온 모든 예외를 ErrorResponse 형태로 바꿔요. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** DB 제약 이름 → 에러 코드. "확인 후 저장" 사이에 동시 요청이 끼어든 경우를 409로 바꿔요. */
    private static final Map<String, ErrorCode> CONSTRAINT_ERRORS = Map.of(
            "uk_users_email", ErrorCode.DUPLICATE_EMAIL,
            "uk_users_username", ErrorCode.DUPLICATE_USERNAME,
            "uk_categories_blog_name", ErrorCode.DUPLICATE_CATEGORY_NAME,
            "uk_reports_post", ErrorCode.ALREADY_REPORTED,
            "uk_reports_comment", ErrorCode.ALREADY_REPORTED);

    /** 서비스 규칙 위반 (가장 많이 쓰여요) */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        return toResponse(e.getErrorCode(), List.of());
    }

    /** @Valid @RequestBody 검증 실패 → 어느 입력칸이 틀렸는지 fieldErrors로 알려줘요 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException e) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return toResponse(ErrorCode.INVALID_INPUT, fieldErrors);
    }

    /** @RequestParam, @PathVariable 검증 실패 (예: 검색어 q가 50자 초과) */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleParamValidation(HandlerMethodValidationException e) {
        List<FieldError> fieldErrors = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldError(
                                result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return toResponse(ErrorCode.INVALID_INPUT, fieldErrors);
    }

    /** JSON 형식 오류, 필수 파라미터 누락, 타입 불일치 (예: ?page=abc) */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        return toResponse(ErrorCode.INVALID_INPUT, List.of());
    }

    /** 업로드 파일이 5MB를 넘을 때 (P-03) */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleFileTooLarge(MaxUploadSizeExceededException e) {
        return toResponse(ErrorCode.FILE_TOO_LARGE, List.of());
    }

    /** 없는 주소, 지원하지 않는 메서드 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return toResponse(ErrorCode.NOT_FOUND, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return toResponse(ErrorCode.METHOD_NOT_ALLOWED, List.of());
    }

    /** DB 제약 위반. 알려진 UNIQUE 제약이면 409, 그 밖에는 코드 버그일 가능성이 커서 500이에요. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
        String constraint = e.getCause() instanceof ConstraintViolationException cve
                ? cve.getConstraintName() : null;
        ErrorCode errorCode = constraint == null ? null : CONSTRAINT_ERRORS.get(constraint);
        if (errorCode == null) {
            log.error("예상하지 못한 DB 제약 위반: {}", constraint, e);
            return toResponse(ErrorCode.INTERNAL_ERROR, List.of());
        }
        return toResponse(errorCode, List.of());
    }

    /** Spring Security 권한 거부가 컨트롤러까지 올라온 경우 (아래 Exception 처리에 먹혀서 500이 되지 않게) */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return toResponse(ErrorCode.FORBIDDEN, List.of());
    }

    /** 예상하지 못한 오류: 원인은 서버 로그에만 남기고, 응답에는 내부 정보를 숨겨요 (NF-06) */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return toResponse(ErrorCode.INTERNAL_ERROR, List.of());
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode, List<FieldError> fieldErrors) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, fieldErrors));
    }
}
