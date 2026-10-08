package com.ownlog.post;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ownlog.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** API 6.5 글 상세: 공개 조건(P-06, B-05), 조회수(C-04), 주인 여부 */
class PostDetailApiTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;

    TestUser nayoung;
    TestUser minsu;

    @BeforeEach
    void setUp() throws Exception {
        nayoung = signupAndLogin("nayoung");
        minsu = signupAndLogin("minsu");
    }

    private Long createPost(String json) throws Exception {
        String body = mockMvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions view(String username, Long postId, TestUser viewer) throws Exception {
        var request = get("/api/blogs/{username}/posts/{postId}", username, postId);
        if (viewer != null) {
            request.header(HttpHeaders.AUTHORIZATION, viewer.bearer());
        }
        return mockMvc.perform(request);
    }

    private Long createPrivateCategory() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO categories (blog_id, name, display_order, visibility, created_at, updated_at)
                VALUES (?, '일기', 1, 'PRIVATE', now(), now()) RETURNING id
                """, Long.class, nayoung.blogId());
    }

    @Test
    @DisplayName("방문자가 공개 글을 보면 조회수가 1 오르고, 태그는 이름순이다")
    void visitorViewsPublicPost() throws Exception {
        Long postId = createPost("""
                {"title":"JPA 정리","content":"본문","topicId":1,"tags":["Spring","jpa","Java"]}
                """);

        view("nayoung", postId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("JPA 정리"))
                .andExpect(jsonPath("$.content").value("본문"))
                .andExpect(jsonPath("$.category.name").value("미분류"))
                .andExpect(jsonPath("$.topic.slug").value("it"))
                .andExpect(jsonPath("$.tags", contains("Java", "jpa", "Spring")))
                .andExpect(jsonPath("$.author.username").value("nayoung"))
                .andExpect(jsonPath("$.blog.name").value("nayoung의 블로그"))
                .andExpect(jsonPath("$.viewCount").value(1))
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.bookmarked").value(false))
                .andExpect(jsonPath("$.isOwner").value(false));
    }

    @Test
    @DisplayName("주인이 보면 조회수가 오르지 않는다 (C-04)")
    void ownerDoesNotIncreaseViewCount() throws Exception {
        Long postId = createPost("""
                {"title":"t","content":"c"}
                """);

        view("nayoung", postId, nayoung)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(0))
                .andExpect(jsonPath("$.isOwner").value(true));
        view("nayoung", postId, minsu).andExpect(jsonPath("$.viewCount").value(1));
    }

    @Test
    @DisplayName("비공개 글은 주인만 볼 수 있고, 나머지는 404")
    void privatePost() throws Exception {
        Long postId = createPost("""
                {"title":"t","content":"c","visibility":"PRIVATE"}
                """);

        view("nayoung", postId, null).andExpect(status().isNotFound());
        view("nayoung", postId, minsu)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        view("nayoung", postId, nayoung).andExpect(status().isOk());
    }

    @Test
    @DisplayName("비공개 카테고리의 공개 글도 방문자에게는 404 (B-05)")
    void publicPostInPrivateCategory() throws Exception {
        Long postId = createPost("""
                {"title":"t","content":"c","categoryId":%d}
                """.formatted(createPrivateCategory()));

        view("nayoung", postId, null).andExpect(status().isNotFound());
        view("nayoung", postId, nayoung)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category.visibility").value("PRIVATE"));
    }

    @Test
    @DisplayName("임시저장 글은 주인이어도 상세로 볼 수 없다")
    void draftIsNotViewable() throws Exception {
        Long draftId = insertPost(nayoung.blogId(), defaultCategoryId(nayoung.blogId()), "DRAFT", "PUBLIC");

        view("nayoung", draftId, nayoung).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("다른 사람 주소로 글을 보면 404 POST_NOT_FOUND, 없는 아이디면 404 USER_NOT_FOUND")
    void wrongAddress() throws Exception {
        Long postId = createPost("""
                {"title":"t","content":"c"}
                """);

        view("minsu", postId, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        view("nobody", postId, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("좋아요와 북마크를 한 회원에게는 liked, bookmarked가 true")
    void likedAndBookmarked() throws Exception {
        Long postId = createPost("""
                {"title":"t","content":"c"}
                """);
        jdbcTemplate.update("INSERT INTO post_likes (user_id, post_id, created_at) VALUES (?, ?, now())", minsu.userId(), postId);
        jdbcTemplate.update("INSERT INTO bookmarks (user_id, post_id, created_at) VALUES (?, ?, now())", minsu.userId(), postId);

        view("nayoung", postId, minsu)
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.bookmarked").value(true));
        view("nayoung", postId, null)
                .andExpect(jsonPath("$.liked").value(false));
    }
}
