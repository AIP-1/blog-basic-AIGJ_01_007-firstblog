package com.ownlog.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ownlog.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API 6.1 ~ 6.4 글 목록. 공개 조건(ERD 4.1)이 모든 목록에 똑같이 적용되는지 확인해요.
 *
 *  글 | 작성자 | 카테고리      | 공개   | 상태   | 토픽   | 발행    | 좋아요
 *  A  | 나영   | 미분류        | 공개   | 발행   | it     | 3일 전  | 10
 *  B  | 나영   | 미분류        | 공개   | 발행   | travel | 1일 전  | 30
 *  C  | 나영   | 미분류        | 비공개 | 발행   |        | 2일 전  | 0
 *  D  | 나영   | 일기(비공개)  | 공개   | 발행   |        | 2일 전  | 0
 *  E  | 나영   | 미분류        | 공개   | 임시   |        |         | 0
 *  F  | 민수   | 미분류        | 공개   | 발행   | it     | 10일 전 | 99
 */
class PostListApiTest extends IntegrationTest {

    private static final long TOPIC_IT = 1L;
    private static final long TOPIC_TRAVEL = 3L;

    @Autowired MockMvc mockMvc;
    @Autowired EntityManagerFactory entityManagerFactory;

    TestUser nayoung;
    TestUser minsu;
    Long diaryCategoryId;

    @BeforeEach
    void setUp() throws Exception {
        nayoung = signupAndLogin("nayoung");
        minsu = signupAndLogin("minsu");
        Long nayoungDefault = defaultCategoryId(nayoung.blogId());
        diaryCategoryId = jdbcTemplate.queryForObject("""
                INSERT INTO categories (blog_id, name, display_order, visibility, created_at, updated_at)
                VALUES (?, '일기', 1, 'PRIVATE', now(), now()) RETURNING id
                """, Long.class, nayoung.blogId());

        Long a = insert(nayoung, nayoungDefault, "A JPA 정리", "연관관계 주인", "PUBLIC", "PUBLISHED", TOPIC_IT, 3, 10);
        insert(nayoung, nayoungDefault, "B 부산 여행", "해운대", "PUBLIC", "PUBLISHED", TOPIC_TRAVEL, 1, 30);
        insert(nayoung, nayoungDefault, "C 비공개", "비밀", "PRIVATE", "PUBLISHED", null, 2, 0);
        insert(nayoung, diaryCategoryId, "D 일기", "비공개 카테고리", "PUBLIC", "PUBLISHED", null, 2, 0);
        insert(nayoung, nayoungDefault, "E 임시저장", "쓰는 중 JPA", "PUBLIC", "DRAFT", null, null, 0);
        insert(minsu, defaultCategoryId(minsu.blogId()), "F 오래된 인기 글", "옛날 이야기", "PUBLIC", "PUBLISHED", TOPIC_IT, 10, 99);

        Long javaTag = jdbcTemplate.queryForObject(
                "INSERT INTO tags (name, created_at) VALUES ('Java', now()) RETURNING id", Long.class);
        jdbcTemplate.update("INSERT INTO post_tags (post_id, tag_id) VALUES (?, ?)", a, javaTag);
    }

    private Long insert(TestUser user, Long categoryId, String title, String content, String visibility,
                        String status, Long topicId, Integer daysAgo, int likes) {
        Timestamp publishedAt = daysAgo == null ? null
                : Timestamp.from(Instant.now().minus(Duration.ofDays(daysAgo)));
        return jdbcTemplate.queryForObject("""
                INSERT INTO posts (blog_id, category_id, topic_id, title, content, summary, status, visibility,
                                   like_count, published_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                RETURNING id
                """, Long.class, user.blogId(), categoryId, topicId, title, content, content,
                status, visibility, likes, publishedAt);
    }

    @Test
    @DisplayName("메인 최신: 볼 수 있는 글만 최신순, 카드에는 본문이 없다")
    void mainLatest() throws Exception {
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", contains("B 부산 여행", "A JPA 정리", "F 오래된 인기 글")))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content[0].author.username").value("nayoung"))
                .andExpect(jsonPath("$.content[0].topic.slug").value("travel"))
                .andExpect(jsonPath("$.content[0].summary").value("해운대"))
                .andExpect(jsonPath("$.content[0].content").doesNotExist());
    }

    @Test
    @DisplayName("메인 + 토픽 필터, 잘못된 토픽·정렬은 400")
    void mainWithTopicAndInvalidParams() throws Exception {
        mockMvc.perform(get("/api/posts").param("topic", "travel"))
                .andExpect(jsonPath("$.content[*].title", contains("B 부산 여행")));
        mockMvc.perform(get("/api/posts").param("topic", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOPIC"));
        mockMvc.perform(get("/api/posts").param("sort", "wrong"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sort"));
    }

    @Test
    @DisplayName("메인 인기: 최근 7일 글을 좋아요 순으로. 10일 전 글은 좋아요가 많아도 빠진다")
    void mainPopular() throws Exception {
        mockMvc.perform(get("/api/posts").param("sort", "popular"))
                .andExpect(jsonPath("$.content[*].title", contains("B 부산 여행", "A JPA 정리")));
    }

    @Test
    @DisplayName("페이지 나누기: size=2의 2페이지")
    void paging() throws Exception {
        mockMvc.perform(get("/api/posts").param("page", "2").param("size", "2"))
                .andExpect(jsonPath("$.content[*].title", contains("F 오래된 인기 글")))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get("/api/posts").param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("검색: 제목·본문 대소문자 무시, 임시저장 글 제외, %는 글자 그대로")
    void search() throws Exception {
        mockMvc.perform(get("/api/posts/search").param("q", "jpa"))
                .andExpect(jsonPath("$.content[*].title", contains("A JPA 정리")));      // E(임시저장)의 본문에도 JPA가 있어요
        mockMvc.perform(get("/api/posts/search").param("q", "해운대"))
                .andExpect(jsonPath("$.content[*].title", contains("B 부산 여행")));      // 본문에서 찾아요
        mockMvc.perform(get("/api/posts/search").param("q", "%"))
                .andExpect(jsonPath("$.content", empty()));
    }

    @Test
    @DisplayName("검색어가 비었거나 50자를 넘으면 400과 fieldErrors(q)")
    void searchValidation() throws Exception {
        mockMvc.perform(get("/api/posts/search").param("q", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("q"));
        mockMvc.perform(get("/api/posts/search").param("q", "가".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("q"));
    }

    @Test
    @DisplayName("태그 모아보기: 대소문자 무시, 대표 표기, 없는 태그는 빈 목록")
    void tagPosts() throws Exception {
        mockMvc.perform(get("/api/tags/{tag}/posts", "java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tag").value("Java"))
                .andExpect(jsonPath("$.posts.content[*].title", contains("A JPA 정리")));
        mockMvc.perform(get("/api/tags/{tag}/posts", "없는태그"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tag").value("없는태그"))
                .andExpect(jsonPath("$.posts.content", empty()))
                .andExpect(jsonPath("$.posts.totalElements").value(0));
    }

    @Test
    @DisplayName("블로그 목록: 방문자는 공개 글만, 주인은 비공개 글과 비공개 카테고리 글까지 (임시저장 제외)")
    void blogPosts() throws Exception {
        mockMvc.perform(get("/api/blogs/nayoung/posts"))
                .andExpect(jsonPath("$.content[*].title", contains("B 부산 여행", "A JPA 정리")));

        mockMvc.perform(get("/api/blogs/nayoung/posts").header(HttpHeaders.AUTHORIZATION, nayoung.bearer()))
                .andExpect(jsonPath("$.content[*].title", contains("B 부산 여행", "D 일기", "C 비공개", "A JPA 정리")))
                .andExpect(jsonPath("$.content[1].categoryVisibility").value("PRIVATE"))
                .andExpect(jsonPath("$.content[2].visibility").value("PRIVATE"));
    }

    @Test
    @DisplayName("블로그 목록 + 비공개 카테고리: 방문자는 빈 목록, 주인은 그 카테고리 글")
    void blogPostsByPrivateCategory() throws Exception {
        mockMvc.perform(get("/api/blogs/nayoung/posts").param("categoryId", diaryCategoryId.toString()))
                .andExpect(jsonPath("$.content", empty()));
        mockMvc.perform(get("/api/blogs/nayoung/posts").param("categoryId", diaryCategoryId.toString())
                        .header(HttpHeaders.AUTHORIZATION, nayoung.bearer()))
                .andExpect(jsonPath("$.content[*].title", contains("D 일기")));
    }

    @Test
    @DisplayName("N+1 없음: 메인 목록은 카드 수와 상관없이 SQL 2개 이하 (목록 + 개수)")
    void noNPlusOne() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(get("/api/posts").param("size", "2")).andExpect(status().isOk());

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
    }
}
