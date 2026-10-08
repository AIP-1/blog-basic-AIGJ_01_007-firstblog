package com.ownlog.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ownlog.IntegrationTest;
import com.ownlog.post.domain.Post;
import com.ownlog.post.repository.PostRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/** API 6.6 발행, 6.7 수정, 6.8 삭제 */
class PostWriteApiTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired PostRepository postRepository;
    @Autowired TransactionTemplate transactionTemplate;

    TestUser nayoung;
    TestUser minsu;

    @BeforeEach
    void setUp() throws Exception {
        nayoung = signupAndLogin("nayoung");
        minsu = signupAndLogin("minsu");
    }

    private ResultActions createPost(TestUser user, String json) throws Exception {
        return mockMvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, user.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Long createPostAndGetId(TestUser user, String json) throws Exception {
        String body = createPost(user, json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions updatePost(TestUser user, Long postId, String json) throws Exception {
        return mockMvc.perform(put("/api/posts/{id}", postId).header(HttpHeaders.AUTHORIZATION, user.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Map<String, Object> postRow(Long postId) {
        return jdbcTemplate.queryForMap("SELECT * FROM posts WHERE id = ?", postId);
    }

    private List<String> tagNames(Long postId) {
        return jdbcTemplate.queryForList("""
                SELECT t.name FROM post_tags pt JOIN tags t ON t.id = pt.tag_id
                 WHERE pt.post_id = ? ORDER BY t.name
                """, String.class, postId);
    }

    @Test
    @DisplayName("최소 입력으로 발행: 미분류, 공개, 요약과 썸네일 자동")
    void createMinimal() throws Exception {
        String body = createPost(nayoung, """
                        {"title":"  JPA 정리  ","content":"## 주인\\n**외래 키** 쪽이에요.\\n![d](/uploads/a.png)"}
                        """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("nayoung"))
                .andReturn().getResponse().getContentAsString();
        Long postId = ((Number) JsonPath.read(body, "$.id")).longValue();

        Map<String, Object> row = postRow(postId);
        assertThat(row.get("title")).isEqualTo("JPA 정리");
        assertThat(row.get("status")).isEqualTo("PUBLISHED");
        assertThat(row.get("published_at")).isNotNull();
        assertThat(row.get("category_id")).isEqualTo(defaultCategoryId(nayoung.blogId()));
        assertThat(row.get("topic_id")).isNull();
        assertThat(row.get("visibility")).isEqualTo("PUBLIC");
        assertThat(row.get("summary")).isEqualTo("주인 외래 키 쪽이에요.");
        assertThat(row.get("thumbnail_url")).isEqualTo("/uploads/a.png");
        assertThat(row.get("view_count")).isEqualTo(0);
        assertThat(row.get("like_count")).isEqualTo(0);
    }

    @Test
    @DisplayName("태그는 대소문자 무시로 합치고, 이미 있는 태그는 다시 만들지 않는다")
    void tags() throws Exception {
        Long first = createPostAndGetId(nayoung, """
                {"title":"t","content":"c","tags":["Java"," jpa ","Spring","java"]}
                """);
        assertThat(tagNames(first)).containsExactlyInAnyOrder("Java", "jpa", "Spring");   // 4개 입력 → 3개

        Long second = createPostAndGetId(minsu, """
                {"title":"t","content":"c","tags":["JAVA"],"topicId":1}
                """);
        assertThat(tagNames(second)).containsExactly("Java");   // 처음 만들어진 표기를 써요
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tags", Long.class)).isEqualTo(3);
        assertThat(postRow(second).get("topic_id")).isEqualTo(1L);
    }

    @Test
    @DisplayName("남의 블로그 카테고리, 없는 토픽은 400")
    void invalidCategoryAndTopic() throws Exception {
        createPost(nayoung, """
                {"title":"t","content":"c","categoryId":%d}
                """.formatted(defaultCategoryId(minsu.blogId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATEGORY"));

        createPost(nayoung, """
                {"title":"t","content":"c","topicId":999}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOPIC"));
    }

    @Test
    @DisplayName("입력 검증: 태그 11개, 공백 태그, javascript 썸네일, 빈 제목")
    void validation() throws Exception {
        createPost(nayoung, """
                {"title":"t","content":"c","tags":["a1","a2","a3","a4","a5","a6","a7","a8","a9","a10","a11"]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("tags"));

        createPost(nayoung, """
                {"title":"t","content":"c","tags":["spring boot"]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        createPost(nayoung, """
                {"title":"t","content":"c","thumbnailUrl":"javascript:alert(1)"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("thumbnailUrl"));

        createPost(nayoung, """
                {"title":"  ","content":"c"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
    }

    @Test
    @DisplayName("카테고리와 공개 범위만 바꾸면 수정일이 생기지 않고, 본문을 바꾸면 생긴다 (P-05)")
    void editedAt() throws Exception {
        Long postId = createPostAndGetId(nayoung, """
                {"title":"t","content":"c","tags":["Java"]}
                """);
        Long devId = jdbcTemplate.queryForObject("""
                INSERT INTO categories (blog_id, name, display_order, created_at, updated_at)
                VALUES (?, '개발', 1, now(), now()) RETURNING id
                """, Long.class, nayoung.blogId());

        updatePost(nayoung, postId, """
                {"title":"t","content":"c","tags":["java"],"categoryId":%d,"visibility":"PRIVATE"}
                """.formatted(devId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editedAt").value(nullValue()));

        updatePost(nayoung, postId, """
                {"title":"t","content":"바뀐 본문","tags":["java"],"categoryId":%d,"visibility":"PRIVATE"}
                """.formatted(devId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editedAt").value(matchesPattern(".*\\+09:00$")));
    }

    @Test
    @DisplayName("남의 공개 글 수정은 403, 비공개 글 수정은 404")
    void otherUserUpdate() throws Exception {
        Long publicPost = createPostAndGetId(nayoung, """
                {"title":"t","content":"c"}
                """);
        Long privatePost = createPostAndGetId(nayoung, """
                {"title":"t","content":"c","visibility":"PRIVATE"}
                """);
        String json = """
                {"title":"가로채기","content":"c"}
                """;

        updatePost(minsu, publicPost, json).andExpect(status().isForbidden());
        updatePost(minsu, privatePost, json)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("삭제하면 댓글·좋아요·북마크·신고·태그 연결이 함께 지워지고 태그 사전은 남는다")
    void deleteWithRelations() throws Exception {
        Long postId = createPostAndGetId(nayoung, """
                {"title":"t","content":"c","tags":["Java"]}
                """);
        Long commentId = jdbcTemplate.queryForObject("""
                INSERT INTO comments (post_id, user_id, content, created_at, updated_at)
                VALUES (?, ?, '댓글', now(), now()) RETURNING id
                """, Long.class, postId, minsu.userId());
        jdbcTemplate.update("INSERT INTO comments (post_id, user_id, parent_id, content, created_at, updated_at) "
                + "VALUES (?, ?, ?, '답글', now(), now())", postId, nayoung.userId(), commentId);
        jdbcTemplate.update("INSERT INTO post_likes (user_id, post_id, created_at) VALUES (?, ?, now())", minsu.userId(), postId);
        jdbcTemplate.update("INSERT INTO bookmarks (user_id, post_id, created_at) VALUES (?, ?, now())", minsu.userId(), postId);
        jdbcTemplate.update("INSERT INTO reports (post_id, reporter_id, reason, created_at) VALUES (?, ?, 'SPAM', now())", postId, minsu.userId());
        jdbcTemplate.update("INSERT INTO reports (comment_id, reporter_id, reason, created_at) VALUES (?, ?, 'SPAM', now())", commentId, nayoung.userId());

        mockMvc.perform(delete("/api/posts/{id}", postId).header(HttpHeaders.AUTHORIZATION, nayoung.bearer()))
                .andExpect(status().isNoContent());

        for (String table : List.of("posts", "post_tags", "comments", "post_likes", "bookmarks", "reports")) {
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class))
                    .as(table).isZero();
        }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tags", Long.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("글을 불러온 뒤 다른 곳에서 좋아요 수가 바뀌어도, 글 수정이 그 값을 덮어쓰지 않는다 (Lost Update 방지)")
    void editDoesNotOverwriteLikeCount() throws Exception {
        Long postId = createPostAndGetId(nayoung, """
                {"title":"t","content":"c"}
                """);

        transactionTemplate.executeWithoutResult(status -> {
            Post post = postRepository.findById(postId).orElseThrow();                 // like_count = 0 을 읽음
            jdbcTemplate.update("UPDATE posts SET like_count = 5 WHERE id = ?", postId); // 그 사이 다른 요청이 좋아요 5개
            post.edit(post.getCategory(), post.getTopic(), "바뀐 제목", post.getContent(),
                    post.getThumbnailUrl(), post.getVisibility(), post.getTags());       // 트랜잭션 끝에 UPDATE
        });

        assertThat(postRow(postId).get("title")).isEqualTo("바뀐 제목");
        assertThat(postRow(postId).get("like_count")).isEqualTo(5);                     // 0으로 덮어쓰지 않았어요
    }
}
