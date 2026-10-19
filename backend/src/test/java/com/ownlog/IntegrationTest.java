package com.ownlog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 통합 테스트 공통 설정: 진짜 PostgreSQL(Testcontainers) + MockMvc + test 프로필.
 * 테스트가 끝날 때마다 데이터를 비워서 테스트끼리 영향을 주지 않게 해요. (topics 초기 데이터는 남겨요)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUpDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE users, blogs, categories, posts, tags, post_tags,
                         comments, post_likes, bookmarks, reports, oauth_authorization_requests
                RESTART IDENTITY CASCADE
                """);
    }

    /** 가입 + 로그인한 테스트 회원. 이메일은 {username}@test.com, 닉네임은 username이에요. */
    protected record TestUser(Long userId, Long blogId, String username, String token) {
        public String bearer() {
            return "Bearer " + token;
        }
    }

    protected TestUser signupAndLogin(String username) throws Exception {
        String email = username + "@test.com";
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"ownlog1234","passwordConfirm":"ownlog1234",
                         "username":"%s","nickname":"%s"}
                        """.formatted(email, username, username)))
                .andExpect(status().isCreated());

        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"ownlog1234"}
                        """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return new TestUser(
                ((Number) JsonPath.read(body, "$.user.id")).longValue(),
                ((Number) JsonPath.read(body, "$.user.blogId")).longValue(),
                username,
                JsonPath.read(body, "$.accessToken"));
    }

    /** 블로그의 기본 카테고리("미분류") id */
    protected Long defaultCategoryId(Long blogId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE blog_id = ? AND is_default", Long.class, blogId);
    }

    /** Post 엔티티 없이 글을 직접 넣어요. status가 PUBLISHED면 발행 시각도 채워요. */
    protected Long insertPost(Long blogId, Long categoryId, String status, String visibility) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO posts (blog_id, category_id, title, content, status, visibility,
                                   published_at, created_at, updated_at)
                VALUES (?, ?, '테스트 글', '본문', ?, ?,
                        CASE WHEN ? = 'PUBLISHED' THEN now() END, now(), now())
                RETURNING id
                """, Long.class, blogId, categoryId, status, visibility, status);
    }
}
