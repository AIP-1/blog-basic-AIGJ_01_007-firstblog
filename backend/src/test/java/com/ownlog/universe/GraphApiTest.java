package com.ownlog.universe;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ownlog.IntegrationTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** API 14.1 기록 그래프 (GR-01 ~ GR-04) */
class GraphApiTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;

    TestUser nayoung;
    TestUser minsu;

    @BeforeEach
    void setUp() throws Exception {
        nayoung = signupAndLogin("nayoung");
        minsu = signupAndLogin("minsu");
    }

    private Long publish(TestUser user, String json) throws Exception {
        String body = mockMvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions graph(TestUser viewer) throws Exception {
        var request = get("/api/blogs/nayoung/graph");
        if (viewer != null) {
            request.header(HttpHeaders.AUTHORIZATION, viewer.bearer());
        }
        return mockMvc.perform(request).andExpect(status().isOk());
    }

    @Test
    @DisplayName("별은 글, 선은 [[링크]](방향)와 같은 태그(공유 개수·태그 이름), 연결 수와 토픽이 있다")
    void nodesAndEdges() throws Exception {
        Long jwt = publish(nayoung, """
                {"title":"JWT","content":"c","topicId":1,"tags":["Spring","JWT","보안"]}""");
        Long sec = publish(nayoung, """
                {"title":"Security","content":"[[%d|JWT]]","tags":["Spring","JWT"]}""".formatted(jwt));
        publish(nayoung, """
                {"title":"일기","content":"c","tags":["일상"]}""");

        graph(null)
                .andExpect(jsonPath("$.nodes", hasSize(3)))
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.edges", hasSize(2)))
                .andExpect(jsonPath("$.edges[?(@.type == 'LINK')].source").value(sec.intValue()))
                .andExpect(jsonPath("$.edges[?(@.type == 'LINK')].target").value(jwt.intValue()))
                .andExpect(jsonPath("$.edges[?(@.type == 'TAG')].weight").value(2))
                .andExpect(jsonPath("$.edges[?(@.type == 'TAG')].tags[*]", containsInAnyOrder("JWT", "Spring")))
                .andExpect(jsonPath("$.nodes[?(@.id == %d)].linkCount".formatted(jwt)).value(1))
                .andExpect(jsonPath("$.nodes[?(@.id == %d)].topic".formatted(jwt)).value("it"))
                .andExpect(jsonPath("$.nodes[?(@.id == %d)].tags[*]".formatted(jwt), hasSize(3)));
    }

    @Test
    @DisplayName("방문자에게 비공개 글은 별로 나오지 않고 그 선도 없다, 주인에게는 나온다")
    void privatePostsOnlyForOwner() throws Exception {
        Long open = publish(nayoung, """
                {"title":"공개","content":"c","tags":["a"]}""");
        publish(nayoung, """
                {"title":"비공개","content":"[[%d|공개]]","visibility":"PRIVATE","tags":["a"]}""".formatted(open));

        graph(minsu).andExpect(jsonPath("$.nodes", hasSize(1))).andExpect(jsonPath("$.edges", hasSize(0)));
        graph(nayoung).andExpect(jsonPath("$.nodes", hasSize(2))).andExpect(jsonPath("$.edges", hasSize(2)));
    }

    @Test
    @DisplayName("방문자에게 봉인 캡슐은 제목·개봉 시각만 있는 잠긴 별이고 선이 없다")
    void sealedCapsuleIsLonelyLockedStar() throws Exception {
        Long open = publish(nayoung, """
                {"title":"공개","content":"c","tags":["비밀"]}""");
        String openAt = OffsetDateTime.now(ZoneOffset.ofHours(9)).plusDays(3).toString();
        Long capsule = publish(nayoung, """
                {"title":"캡슐","content":"[[%d|공개]]","topicId":2,"tags":["비밀"],"openAt":"%s"}""".formatted(open, openAt));

        graph(minsu)
                .andExpect(jsonPath("$.nodes", hasSize(2)))
                .andExpect(jsonPath("$.edges", hasSize(0)))
                .andExpect(jsonPath("$.nodes[?(@.id == %d)].locked".formatted(capsule)).value(true))
                .andExpect(jsonPath("$.nodes[?(@.id == %d)].title".formatted(capsule)).value("캡슐"))
                .andExpect(jsonPath("$.nodes[?(@.id == %d)].openAt".formatted(capsule)).isNotEmpty())
                .andExpect(jsonPath("$.nodes[?(@.id == %d)].topic".formatted(capsule)).doesNotExist())
                .andExpect(jsonPath("$.nodes[?(@.id == %d)].tags".formatted(capsule)).doesNotExist());

        graph(nayoung).andExpect(jsonPath("$.edges", hasSize(2)));
    }

    @Test
    @DisplayName("별은 최신 300개까지만, 넘으면 truncated")
    void limitsNodes() {
        Long category = defaultCategoryId(nayoung.blogId());
        jdbcTemplate.update("""
                INSERT INTO posts (blog_id, category_id, title, content, status, visibility, published_at, created_at, updated_at)
                SELECT ?, ?, '글 ' || g, '본문', 'PUBLISHED', 'PUBLIC', now() - g * interval '1 minute', now(), now()
                  FROM generate_series(1, 305) g
                """, nayoung.blogId(), category);

        try {
            graph(null).andExpect(jsonPath("$.nodes", hasSize(300))).andExpect(jsonPath("$.truncated").value(true))
                    .andExpect(jsonPath("$.nodes[0].title").value("글 1"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("별에는 타임랩스용 writtenAt(캡슐은 봉인 시각)과 블로그 정보가 있다 (GR-05)")
    void nodesHaveWrittenAtAndBlog() throws Exception {
        String openAt = OffsetDateTime.now(ZoneOffset.ofHours(9)).plusDays(3).toString();
        Long capsule = publish(nayoung, """
                {"title":"캡슐","content":"c","openAt":"%s"}""".formatted(openAt));
        graph(minsu)
                .andExpect(jsonPath("$.nodes[0].writtenAt").isNotEmpty())
                .andExpect(jsonPath("$.nodes[?(@.id == %d)].writtenAt".formatted(capsule)).isNotEmpty())
                .andExpect(jsonPath("$.nodes[0].username").value("nayoung"));
    }

    @Test
    @DisplayName("서비스 전체 우주: 모든 블로그의 공개 글, 블로그 사이 링크, 비공개·봉인 캡슐 제외 (GR-06, 14.3)")
    void universeAcrossBlogs() throws Exception {
        Long mine = publish(nayoung, """
                {"title":"나영 글","content":"c","tags":["공통"]}""");
        publish(minsu, """
                {"title":"민수 글","content":"[[%d|나영 글]]","tags":["공통"]}""".formatted(mine));
        publish(minsu, """
                {"title":"민수 비공개","content":"c","visibility":"PRIVATE"}""");
        String openAt = OffsetDateTime.now(ZoneOffset.ofHours(9)).plusDays(3).toString();
        publish(minsu, """
                {"title":"민수 캡슐","content":"c","openAt":"%s"}""".formatted(openAt));

        mockMvc.perform(get("/api/universe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes", hasSize(2)))
                .andExpect(jsonPath("$.nodes[*].username", containsInAnyOrder("nayoung", "minsu")))
                .andExpect(jsonPath("$.edges", hasSize(2)))
                .andExpect(jsonPath("$.edges[?(@.type == 'LINK')].target").value(mine.intValue()));
    }

    @Test
    @DisplayName("글이 없으면 빈 우주, 없는 블로그는 404")
    void emptyAndNotFound() throws Exception {
        graph(null).andExpect(jsonPath("$.nodes", hasSize(0))).andExpect(jsonPath("$.edges", hasSize(0)));
        mockMvc.perform(get("/api/blogs/nobody/graph")).andExpect(status().isNotFound());
    }
}
