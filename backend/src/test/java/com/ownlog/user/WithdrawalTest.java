package com.ownlog.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ownlog.IntegrationTest;
import com.ownlog.user.repository.WithdrawalCleaner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * API 4.7 탈퇴 (U-06, ERD 5장). 나영이 탈퇴하고, 민수·지수가 관련 데이터를 갖고 있어요.
 *
 * - 나영의 글(태그 Java): 민수 댓글, 민수 좋아요, 민수 북마크, 민수의 글 신고, 지수의 댓글 신고
 * - 민수의 글: 나영 좋아요, 나영 댓글, 나영 북마크, 나영의 글 신고
 */
class WithdrawalTest extends IntegrationTest {

    private static final Path PROFILE_FILE = Path.of("build/test-uploads/profile/nayoung.png");

    @Autowired MockMvc mockMvc;
    @MockitoSpyBean WithdrawalCleaner withdrawalCleaner;   // 롤백 테스트에서만 일부러 실패시켜요

    TestUser nayoung;
    TestUser minsu;
    TestUser jisoo;
    Long nayoungPost;
    Long minsuPost;
    Long minsuCommentOnNayoung;
    Long nayoungCommentOnMinsu;

    @BeforeEach
    void setUp() throws Exception {
        nayoung = signupAndLogin("nayoung");
        minsu = signupAndLogin("minsu");
        jisoo = signupAndLogin("jisoo");

        Files.createDirectories(PROFILE_FILE.getParent());
        Files.writeString(PROFILE_FILE, "image");
        call(put("/api/users/me"), nayoung,
                "{\"nickname\":\"나영\",\"bio\":\"소개\",\"profileImageUrl\":\"/uploads/profile/nayoung.png\"}")
                .andExpect(status().isOk());

        nayoungPost = idOf(call(post("/api/posts"), nayoung, "{\"title\":\"나영 글\",\"content\":\"c\",\"tags\":[\"Java\"]}"));
        minsuPost = idOf(call(post("/api/posts"), minsu, "{\"title\":\"민수 글\",\"content\":\"c\"}"));

        minsuCommentOnNayoung = idOf(call(post("/api/posts/{id}/comments", nayoungPost), minsu, "{\"content\":\"민수 댓글\"}"));
        call(put("/api/posts/{id}/like", nayoungPost), minsu, null);
        call(put("/api/posts/{id}/bookmark", nayoungPost), minsu, null);
        call(post("/api/posts/{id}/reports", nayoungPost), minsu, "{\"reason\":\"SPAM\"}");
        call(post("/api/comments/{id}/reports", minsuCommentOnNayoung), jisoo, "{\"reason\":\"SPAM\"}");

        call(put("/api/posts/{id}/like", minsuPost), nayoung, null);
        nayoungCommentOnMinsu = idOf(call(post("/api/posts/{id}/comments", minsuPost), nayoung, "{\"content\":\"나영 댓글\"}"));
        call(put("/api/posts/{id}/bookmark", minsuPost), nayoung, null);
        call(post("/api/posts/{id}/reports", minsuPost), nayoung, "{\"reason\":\"SPAM\"}");
    }

    private ResultActions call(MockHttpServletRequestBuilder request, TestUser user, String json) throws Exception {
        if (user != null) {
            request.header(HttpHeaders.AUTHORIZATION, user.bearer());
        }
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    private Long idOf(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions withdraw(String password) throws Exception {
        return call(delete("/api/users/me"), nayoung, "{\"password\":\"%s\"}".formatted(password));
    }

    private long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 400이고 아무것도 지워지지 않는다")
    void wrongPassword() throws Exception {
        withdraw("wrong1234")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));

        assertThat(count("SELECT COUNT(*) FROM blogs WHERE user_id = ?", nayoung.userId())).isEqualTo(1);
        assertThat(count("SELECT like_count FROM posts WHERE id = ?", minsuPost)).isEqualTo(1);
    }

    @Test
    @DisplayName("탈퇴: 내 블로그와 거기 딸린 것은 모두 지워지고, 남의 글의 좋아요 수가 보정된다")
    void withdrawDeletesOwnedData() throws Exception {
        withdraw("ownlog1234").andExpect(status().isNoContent());

        // 나영의 블로그, 카테고리, 글, 태그 연결
        assertThat(count("SELECT COUNT(*) FROM blogs WHERE user_id = ?", nayoung.userId())).isZero();
        assertThat(count("SELECT COUNT(*) FROM categories WHERE blog_id = ?", nayoung.blogId())).isZero();
        assertThat(count("SELECT COUNT(*) FROM posts WHERE id = ?", nayoungPost)).isZero();
        assertThat(count("SELECT COUNT(*) FROM post_tags")).isZero();
        assertThat(count("SELECT COUNT(*) FROM tags")).isEqualTo(1);                // 태그 사전은 남아요

        // 나영 글에 딸린 민수 댓글·좋아요·북마크·신고, 지수의 댓글 신고
        assertThat(count("SELECT COUNT(*) FROM comments WHERE id = ?", minsuCommentOnNayoung)).isZero();
        assertThat(count("SELECT COUNT(*) FROM post_likes WHERE post_id = ?", nayoungPost)).isZero();
        assertThat(count("SELECT COUNT(*) FROM bookmarks WHERE post_id = ?", nayoungPost)).isZero();
        assertThat(count("SELECT COUNT(*) FROM reports WHERE post_id = ? OR comment_id = ?",
                nayoungPost, minsuCommentOnNayoung)).isZero();

        // 나영이 민수 글에 누른 좋아요는 지워지고 좋아요 수가 줄어요. 북마크도 지워져요
        assertThat(count("SELECT COUNT(*) FROM post_likes WHERE user_id = ?", nayoung.userId())).isZero();
        assertThat(count("SELECT like_count FROM posts WHERE id = ?", minsuPost)).isZero();
        assertThat(count("SELECT COUNT(*) FROM bookmarks WHERE user_id = ?", nayoung.userId())).isZero();

        // 나영이 민수 글에 단 댓글과 나영이 한 신고는 남아요
        assertThat(count("SELECT COUNT(*) FROM comments WHERE id = ?", nayoungCommentOnMinsu)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM reports WHERE reporter_id = ?", nayoung.userId())).isEqualTo(1);
    }

    @Test
    @DisplayName("탈퇴: 개인정보는 익명화하고 아이디만 남기며, 프로필 사진 파일은 커밋 후 지워진다")
    void withdrawAnonymizes() throws Exception {
        withdraw("ownlog1234").andExpect(status().isNoContent());

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM users WHERE id = ?", nayoung.userId());
        assertThat(row).containsEntry("status", "DELETED")
                .containsEntry("email", "deleted_" + nayoung.userId() + "@ownlog.invalid")
                .containsEntry("nickname", "탈퇴한 사용자");
        assertThat(row.get("bio")).isNull();
        assertThat(row.get("profile_image_url")).isNull();
        assertThat(row).containsEntry("username", "nayoung");
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(PROFILE_FILE).doesNotExist();

        call(get("/api/posts/{id}/comments", minsuPost), null, null)
                .andExpect(jsonPath("$.comments[0].content").value("나영 댓글"))
                .andExpect(jsonPath("$.comments[0].author.nickname").value("탈퇴한 사용자"));
    }

    @Test
    @DisplayName("탈퇴 후: 예전 토큰은 401, 같은 이메일로 재가입은 되고, 같은 아이디는 쓸 수 없다")
    void afterWithdrawal() throws Exception {
        withdraw("ownlog1234").andExpect(status().isNoContent());

        call(get("/api/users/me"), nayoung, null).andExpect(status().isUnauthorized());
        call(post("/api/auth/signup"), null, """
                {"email":"nayoung@test.com","password":"ownlog1234","passwordConfirm":"ownlog1234",
                 "username":"nayoung_new","nickname":"나영"}
                """).andExpect(status().isCreated());
        call(get("/api/auth/check-username").param("username", "nayoung"), null, null)
                .andExpect(jsonPath("$.reason").value("DUPLICATE"));
    }

    @Test
    @DisplayName("📚 중간에 실패하면 전부 롤백되고, 프로필 사진 파일도 지워지지 않는다")
    void rollbackOnFailure() throws Exception {
        // ①(블로그 삭제)과 ②(좋아요 정리)까지 실행된 뒤 ③에서 일부러 실패시켜요
        doThrow(new RuntimeException("강제 실패")).when(withdrawalCleaner).deleteBookmarksOf(anyLong());

        withdraw("ownlog1234").andExpect(status().isInternalServerError());

        assertThat(count("SELECT COUNT(*) FROM blogs WHERE user_id = ?", nayoung.userId())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM posts WHERE id = ?", nayoungPost)).isEqualTo(1);
        assertThat(count("SELECT like_count FROM posts WHERE id = ?", minsuPost)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM users WHERE id = ?", String.class, nayoung.userId()))
                .isEqualTo("ACTIVE");
        assertThat(PROFILE_FILE).exists();   // AFTER_COMMIT 리스너는 롤백되면 실행되지 않아요
    }
}
