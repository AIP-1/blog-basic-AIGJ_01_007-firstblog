package com.ownlog.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** 삭제와 동시에 들어온 요청의 외래키 위반을 어떻게 응답하는지 (전체 점검 M2) */
class ForeignKeyErrorMappingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private ErrorCode codeOf(String constraint, String sqlMessage) {
        var cause = new ConstraintViolationException("x", new SQLException(sqlMessage, "23503"), constraint);
        var response = handler.handleDataIntegrity(new DataIntegrityViolationException("x", cause));
        return ErrorCode.valueOf(response.getBody().code());
    }

    @Test
    @DisplayName("방금 지워진 글·댓글·카테고리를 참조하려던 저장 → 404")
    void insertSideBecomesNotFound() {
        String insert = "insert or update on table violates foreign key constraint";
        assertThat(codeOf("post_likes_post_id_fkey", insert)).isEqualTo(ErrorCode.POST_NOT_FOUND);
        assertThat(codeOf("post_links_target_post_id_fkey", insert)).isEqualTo(ErrorCode.POST_NOT_FOUND);
        assertThat(codeOf("comments_parent_id_fkey", insert)).isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
        assertThat(codeOf("posts_category_id_fkey", insert)).isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("지우는 쪽이 막힌 경우(아직 참조 중)와 모르는 제약은 500 그대로 (다시 시도하면 돼요)")
    void deleteSideAndUnknownStayInternal() {
        assertThat(codeOf("post_likes_post_id_fkey", "update or delete on table \"posts\" violates foreign key constraint ... is still referenced"))
                .isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(codeOf("ck_posts_capsule_order", "check")).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(codeOf("uk_users_email", "dup")).isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }
}
