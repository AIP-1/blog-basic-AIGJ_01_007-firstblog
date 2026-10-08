package com.ownlog.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ownlog.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** API 10.1 글 신고, 10.2 댓글 신고 (R-01 ~ R-04) */
class ReportApiTest extends IntegrationTest {

    private static final String SPAM = "{\"reason\":\"SPAM\"}";

    @Autowired MockMvc mockMvc;

    TestUser nayoung;   // 글 주인
    TestUser minsu;
    Long postId;

    @BeforeEach
    void setUp() throws Exception {
        nayoung = signupAndLogin("nayoung");
        minsu = signupAndLogin("minsu");
        postId = insertPost(nayoung.blogId(), defaultCategoryId(nayoung.blogId()), "PUBLISHED", "PUBLIC");
    }

    private ResultActions reportPost(TestUser user, Long targetPostId, String json) throws Exception {
        var request = post("/api/posts/{id}/reports", targetPostId).contentType(MediaType.APPLICATION_JSON).content(json);
        if (user != null) {
            request.header(HttpHeaders.AUTHORIZATION, user.bearer());
        }
        return mockMvc.perform(request);
    }

    private ResultActions reportComment(TestUser user, Long commentId, String json) throws Exception {
        return mockMvc.perform(post("/api/comments/{id}/reports", commentId)
                .header(HttpHeaders.AUTHORIZATION, user.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Long insertComment(Long targetPostId, TestUser writer) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO comments (post_id, user_id, content, created_at, updated_at)
                VALUES (?, ?, '댓글', now(), now()) RETURNING id
                """, Long.class, targetPostId, writer.userId());
    }

    private Map<String, Object> reportRow(String body) {
        Long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        return jdbcTemplate.queryForMap("SELECT * FROM reports WHERE id = ?", id);
    }

    @Test
    @DisplayName("글 신고: 201, 대상은 글 하나, 상태는 RECEIVED")
    void reportPostSuccess() throws Exception {
        String body = reportPost(minsu, postId, SPAM)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> row = reportRow(body);
        assertThat(row.get("post_id")).isEqualTo(postId);
        assertThat(row.get("comment_id")).isNull();
        assertThat(row.get("reporter_id")).isEqualTo(minsu.userId());
        assertThat(row.get("status")).isEqualTo("RECEIVED");
    }

    @Test
    @DisplayName("내 글 신고는 400, 같은 글 두 번째 신고는 409")
    void ownAndDuplicate() throws Exception {
        reportPost(nayoung, postId, SPAM)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_REPORT_OWN_CONTENT"));

        reportPost(minsu, postId, SPAM).andExpect(status().isCreated());
        reportPost(minsu, postId, SPAM)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REPORTED"));
    }

    @Test
    @DisplayName("\"기타\"는 상세 사유가 필수 (없거나 공백이면 400), 있으면 201")
    void etcRequiresDetail() throws Exception {
        reportPost(minsu, postId, "{\"reason\":\"ETC\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("detail"));
        reportPost(minsu, postId, "{\"reason\":\"ETC\",\"detail\":\"   \"}")
                .andExpect(status().isBadRequest());

        String body = reportPost(minsu, postId, "{\"reason\":\"ETC\",\"detail\":\" 다른 블로그 글을 복사했어요 \"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(reportRow(body).get("detail")).isEqualTo("다른 블로그 글을 복사했어요");
    }

    @Test
    @DisplayName("잘못된 사유 값이나 사유 생략은 400")
    void invalidReason() throws Exception {
        reportPost(minsu, postId, "{\"reason\":\"WRONG\"}").andExpect(status().isBadRequest());
        reportPost(minsu, postId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
    }

    @Test
    @DisplayName("볼 수 없는 글(비공개, 임시저장)은 404, 방문자는 401")
    void accessRules() throws Exception {
        Long privatePost = insertPost(nayoung.blogId(), defaultCategoryId(nayoung.blogId()), "PUBLISHED", "PRIVATE");
        Long draft = insertPost(nayoung.blogId(), defaultCategoryId(nayoung.blogId()), "DRAFT", "PUBLIC");

        reportPost(minsu, privatePost, SPAM)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        reportPost(minsu, draft, SPAM).andExpect(status().isNotFound());
        reportPost(null, postId, SPAM).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("댓글 신고: 201, 대상은 댓글 하나")
    void reportCommentSuccess() throws Exception {
        Long commentId = insertComment(postId, nayoung);

        String body = reportComment(minsu, commentId, SPAM)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> row = reportRow(body);
        assertThat(row.get("comment_id")).isEqualTo(commentId);
        assertThat(row.get("post_id")).isNull();
    }

    @Test
    @DisplayName("내 댓글, 삭제 표시된 댓글은 400, 같은 댓글 두 번째 신고는 409")
    void commentRules() throws Exception {
        Long mine = insertComment(postId, minsu);
        Long deleted = insertComment(postId, nayoung);
        jdbcTemplate.update("UPDATE comments SET is_deleted = true WHERE id = ?", deleted);
        Long normal = insertComment(postId, nayoung);

        reportComment(minsu, mine, SPAM).andExpect(jsonPath("$.code").value("CANNOT_REPORT_OWN_CONTENT"));
        reportComment(minsu, deleted, SPAM)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_REPORT_DELETED_COMMENT"));

        reportComment(minsu, normal, SPAM).andExpect(status().isCreated());
        reportComment(minsu, normal, SPAM).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("비공개 글의 댓글은 404 COMMENT_NOT_FOUND (명세 10.2)")
    void commentOnHiddenPost() throws Exception {
        Long privatePost = insertPost(nayoung.blogId(), defaultCategoryId(nayoung.blogId()), "PUBLISHED", "PRIVATE");
        Long commentId = insertComment(privatePost, nayoung);

        reportComment(minsu, commentId, SPAM)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("같은 사람이 글과 그 글의 댓글을 각각 신고하는 건 중복이 아니다")
    void postAndCommentAreDifferentTargets() throws Exception {
        Long commentId = insertComment(postId, nayoung);

        reportPost(minsu, postId, SPAM).andExpect(status().isCreated());
        reportComment(minsu, commentId, SPAM).andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reports", Long.class)).isEqualTo(2);
    }
}
