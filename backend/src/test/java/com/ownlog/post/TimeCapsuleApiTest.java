package com.ownlog.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ownlog.IntegrationTest;
import java.time.Duration;
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

/** 타임캡슐 (기록유니버스-설계.md 3장, TC-01 ~ TC-06) */
class TimeCapsuleApiTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;

    TestUser nayoung;
    TestUser minsu;

    @BeforeEach
    void setUp() throws Exception {
        nayoung = signupAndLogin("nayoung");
        minsu = signupAndLogin("minsu");
    }

    private static String inFuture(Duration duration) {
        return OffsetDateTime.now(ZoneOffset.ofHours(9)).plus(duration).toString();
    }

    private ResultActions publish(String json) throws Exception {
        return mockMvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** 1시간 뒤 열리는 공개 캡슐 (태그 "비밀") */
    private Long publishCapsule() throws Exception {
        String body = publish("""
                {"title":"1년 뒤의 나에게","content":"비밀 본문 키워드캡슐","tags":["비밀"],"topicId":1,"openAt":"%s"}
                """.formatted(inFuture(Duration.ofHours(1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** 시간이 흐른 것처럼: 개봉 시각을 과거로 당겨요 (봉인 시각은 그보다 앞으로) */
    private void open(Long postId) {
        jdbcTemplate.update("""
                UPDATE posts SET sealed_at = now() - interval '2 days', published_at = now() - interval '1 minute'
                 WHERE id = ?
                """, postId);
    }

    private ResultActions getAs(String url, TestUser viewer) throws Exception {
        var request = get(url);
        if (viewer != null) {
            request.header(HttpHeaders.AUTHORIZATION, viewer.bearer());
        }
        return mockMvc.perform(request);
    }

    @Test
    @DisplayName("열리는 시각이 1분 미만, 과거, 10년 초과면 INVALID_OPEN_AT (TC-02)")
    void rejectsInvalidOpenAt() throws Exception {
        for (Duration d : new Duration[] {Duration.ofSeconds(20), Duration.ofHours(-1), Duration.ofDays(3660)}) {
            publish("""
                    {"title":"t","content":"c","openAt":"%s"}
                    """.formatted(inFuture(d)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_OPEN_AT"));
        }
        publish("""
                {"title":"t","content":"c","openAt":"%s"}
                """.formatted(inFuture(Duration.ofSeconds(61))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("봉인 글은 메인·검색·태그·토픽·인기·블로그 목록(방문자)·사이드바 글 수에 나오지 않는다 (TC-03)")
    void sealedCapsuleIsHiddenFromLists() throws Exception {
        publishCapsule();

        getAs("/api/posts", null).andExpect(jsonPath("$.totalElements").value(0));
        getAs("/api/posts?sort=popular", null).andExpect(jsonPath("$.totalElements").value(0));
        getAs("/api/posts?topic=it", null).andExpect(jsonPath("$.totalElements").value(0));
        getAs("/api/posts/search?q=키워드캡슐", null).andExpect(jsonPath("$.totalElements").value(0));
        getAs("/api/tags/비밀/posts", null).andExpect(jsonPath("$.posts.totalElements").value(0));
        getAs("/api/blogs/nayoung/posts", minsu).andExpect(jsonPath("$.totalElements").value(0));
        getAs("/api/blogs/nayoung/categories", minsu).andExpect(jsonPath("$.categories[0].postCount").value(0));

        // 주인에게는 블로그 목록에 보이고, 카드에 봉인 시각이 있어요
        getAs("/api/blogs/nayoung/posts", nayoung)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sealedAt").isNotEmpty());
    }

    @Test
    @DisplayName("방문자가 봉인 글을 열면 잠금 응답: 제목·봉인·개봉 시각만, 조회수는 그대로 (TC-03)")
    void visitorGetsLockedDetail() throws Exception {
        Long id = publishCapsule();

        getAs("/api/blogs/nayoung/posts/" + id, minsu)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("1년 뒤의 나에게"))
                .andExpect(jsonPath("$.content").value(nullValue()))
                .andExpect(jsonPath("$.topic").value(nullValue()))
                .andExpect(jsonPath("$.tags", hasSize(0)))
                .andExpect(jsonPath("$.timeCapsule.locked").value(true))
                .andExpect(jsonPath("$.timeCapsule.openAt").isNotEmpty())
                .andExpect(jsonPath("$.timeCapsule.sealedAt").isNotEmpty());

        Integer views = jdbcTemplate.queryForObject("SELECT view_count FROM posts WHERE id = ?", Integer.class, id);
        assertThat(views).isZero();
    }

    @Test
    @DisplayName("비공개 캡슐은 방문자에게 잠금 카드도 아닌 404")
    void privateCapsuleIsNotFound() throws Exception {
        String body = publish("""
                {"title":"t","content":"c","visibility":"PRIVATE","openAt":"%s"}
                """.formatted(inFuture(Duration.ofHours(1))))
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        getAs("/api/blogs/nayoung/posts/" + id, minsu).andExpect(status().isNotFound());
        getAs("/api/blogs/nayoung/time-capsules", minsu).andExpect(jsonPath("$", hasSize(0)));
        getAs("/api/blogs/nayoung/time-capsules", nayoung).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("봉인 글에 댓글·좋아요·북마크·신고하면 404 (존재를 숨겨요)")
    void reactionsOnSealedCapsuleAreNotFound() throws Exception {
        Long id = publishCapsule();
        String auth = minsu.bearer();

        mockMvc.perform(post("/api/posts/{id}/comments", id).header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hi\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/posts/{id}/like", id).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/posts/{id}/bookmark", id).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/posts/{id}/reports", id).header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isNotFound());
        getAs("/api/posts/" + id + "/comments", minsu).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("작성자는 봉인 중에도 본문을 보지만 수정은 TIME_CAPSULE_SEALED, 삭제는 된다 (TC-04)")
    void ownerCanReadButNotEdit() throws Exception {
        Long id = publishCapsule();

        getAs("/api/blogs/nayoung/posts/" + id, nayoung)
                .andExpect(jsonPath("$.content").value("비밀 본문 키워드캡슐"))
                .andExpect(jsonPath("$.timeCapsule.locked").value(false));

        mockMvc.perform(put("/api/posts/{id}", id).header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"바꿈\",\"content\":\"c\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TIME_CAPSULE_SEALED"));

        mockMvc.perform(delete("/api/posts/{id}", id).header(HttpHeaders.AUTHORIZATION, nayoung.bearer()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("개봉 시각이 지나면 모든 곳에 나오고 반응·수정이 되며, 최근 열린 타임캡슐에 나온다 (TC-05, TC-06)")
    void openedCapsuleBehavesLikeNormalPost() throws Exception {
        Long id = publishCapsule();
        insertPost(nayoung.blogId(), defaultCategoryId(nayoung.blogId()), "PUBLISHED", "PUBLIC");   // 일반 글
        open(id);

        getAs("/api/posts", null).andExpect(jsonPath("$.totalElements").value(2));
        getAs("/api/posts/search?q=키워드캡슐", null).andExpect(jsonPath("$.totalElements").value(1));
        getAs("/api/blogs/nayoung/posts/" + id, minsu)
                .andExpect(jsonPath("$.content").value("비밀 본문 키워드캡슐"))
                .andExpect(jsonPath("$.timeCapsule.locked").value(false))
                .andExpect(jsonPath("$.viewCount").value(1));
        mockMvc.perform(put("/api/posts/{id}/like", id).header(HttpHeaders.AUTHORIZATION, minsu.bearer()))
                .andExpect(status().isOk());

        getAs("/api/time-capsules/opened", null)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].sealedAt").isNotEmpty());
        getAs("/api/blogs/nayoung/time-capsules", minsu).andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(put("/api/posts/{id}", id).header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"열린 뒤 수정\",\"content\":\"c\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("곧 열리는 타임캡슐은 개봉 빠른 순이고 본문 정보가 없다 (12.1)")
    void sealedListIsOrderedByOpenAt() throws Exception {
        publish("""
                {"title":"나중","content":"c","openAt":"%s"}
                """.formatted(inFuture(Duration.ofDays(30)))).andExpect(status().isCreated());
        publish("""
                {"title":"먼저","content":"c","openAt":"%s"}
                """.formatted(inFuture(Duration.ofDays(1)))).andExpect(status().isCreated());

        getAs("/api/blogs/nayoung/time-capsules", null)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("먼저"))
                .andExpect(jsonPath("$[1].title").value("나중"))
                .andExpect(jsonPath("$[0].content").doesNotExist());
    }

    @Test
    @DisplayName("임시저장 글도 타임캡슐로 발행할 수 있다")
    void draftCanBePublishedAsCapsule() throws Exception {
        String body = mockMvc.perform(post("/api/drafts").header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"초안\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        mockMvc.perform(post("/api/drafts/{id}/publish", id).header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title":"초안","content":"c","openAt":"%s"}
                                """.formatted(inFuture(Duration.ofHours(2)))))
                .andExpect(status().isOk());

        getAs("/api/blogs/nayoung/posts/" + id, minsu).andExpect(jsonPath("$.timeCapsule.locked").value(true));
        getAs("/api/posts", null).andExpect(jsonPath("$.totalElements").value(0));
    }
}
