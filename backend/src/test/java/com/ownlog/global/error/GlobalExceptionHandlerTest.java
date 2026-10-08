package com.ownlog.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** DB 제약 위반 → 에러 코드 변환 (동시 가입처럼 재현하기 어려운 상황을 직접 만들어서 확인해요) */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private DataIntegrityViolationException violation(String constraintName) {
        return new DataIntegrityViolationException("중복",
                new ConstraintViolationException("중복", new SQLException(), constraintName));
    }

    @Test
    @DisplayName("알려진 UNIQUE 제약 위반은 409와 해당 에러 코드로 바뀐다")
    void knownConstraint() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(violation("uk_users_username"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("DUPLICATE_USERNAME");
    }

    @Test
    @DisplayName("알 수 없는 제약 위반은 내부 정보를 숨기고 500으로 응답한다")
    void unknownConstraint() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(violation("ck_posts_published_required"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
    }
}
