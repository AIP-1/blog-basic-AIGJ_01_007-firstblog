package com.ownlog.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** API 7.1 ~ 7.6 임시저장 (D-01 ~ D-07) */
class DraftApiTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;

    TestUser nayoung;
    TestUser minsu;

    @BeforeEach
    void setUp() throws Exception {
        nayoung = signupAndLogin("nayoung");
        minsu = signupAndLogin("minsu");
    }

    private ResultActions call(MockHttpServletRequestBuilder request, TestUser user, String json) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, user.bearer());
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    private Long createDraft(TestUser user, String json) throws Exception {
        String body = call(post("/api/drafts"), user, json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private Map<String, Object> postRow(Long postId) {
        return jdbcTemplate.queryForMap("SELECT * FROM posts WHERE id = ?", postId);
    }

    @Test
    @DisplayName("아무것도 안 쓰고 임시저장해도 된다 (D-03)")
    void createEmptyDraft() throws Exception {
        String body = call(post("/api/drafts"), nayoung, "{}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.updatedAt").value(matchesPattern(".*\\+09:00$")))
                .andReturn().getResponse().getContentAsString();
        Long draftId = ((Number) JsonPath.read(body, "$.id")).longValue();

        Map<String, Object> row = postRow(draftId);
        assertThat(row).containsEntry("status", "DRAFT");
        assertThat(row.get("title")).isNull();
        assertThat(row.get("published_at")).isNull();
        assertThat(row).containsEntry("category_id", defaultCategoryId(nayoung.blogId()));
    }

    @Test
    @DisplayName("다시 저장하면 같은 글을 덮어쓴다 (D-02)")
    void overwrite() throws Exception {
        Long draftId = createDraft(nayoung, """
                {"title":"  ","content":"처음"}
                """);

        call(put("/api/drafts/{id}", draftId), nayoung, """
                {"title":"제목","content":"**고친** 본문"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(draftId));

        Map<String, Object> row = postRow(draftId);
        assertThat(row).containsEntry("title", "제목");
        assertThat(row).containsEntry("summary", "고친 본문");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM posts", Long.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("목록: 최근 저장 순, 제목 없으면 null, 발행 글과 남의 임시저장 글은 안 나온다")
    void listDrafts() throws Exception {
        Long first = createDraft(nayoung, "{\"content\":\"첫 번째\"}");
        Long second = createDraft(nayoung, "{\"title\":\"두 번째\"}");
        createDraft(minsu, "{\"title\":\"민수 것\"}");
        insertPost(nayoung.blogId(), defaultCategoryId(nayoung.blogId()), "PUBLISHED", "PUBLIC");

        call(put("/api/drafts/{id}", first), nayoung, "{\"content\":\"첫 번째 (다시 저장)\"}")
                .andExpect(status().isOk());

        call(get("/api/drafts"), nayoung, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id", contains(first.intValue(), second.intValue())))
                .andExpect(jsonPath("$.content[0].title").value(nullValue()))
                .andExpect(jsonPath("$.content[0].summary").value("첫 번째 (다시 저장)"));
    }

    @Test
    @DisplayName("불러오기: 본문, 카테고리, 이름순 태그")
    void getDraft() throws Exception {
        Long draftId = createDraft(nayoung, """
                {"title":"t","content":"쓰는 중","tags":["Spring","java"],"topicId":3}
                """);

        call(get("/api/drafts/{id}", draftId), nayoung, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("쓰는 중"))
                .andExpect(jsonPath("$.category.name").value("미분류"))
                .andExpect(jsonPath("$.topic.slug").value("travel"))
                .andExpect(jsonPath("$.tags", contains("java", "Spring")));
    }

    @Test
    @DisplayName("남의 임시저장 글은 불러오기·저장·삭제·발행 모두 404 DRAFT_NOT_FOUND")
    void otherUsersDraft() throws Exception {
        Long draftId = createDraft(nayoung, "{\"title\":\"나영의 비밀 원고\"}");
        String publishJson = "{\"title\":\"t\",\"content\":\"c\"}";

        call(get("/api/drafts/{id}", draftId), minsu, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRAFT_NOT_FOUND"));
        call(put("/api/drafts/{id}", draftId), minsu, "{}").andExpect(status().isNotFound());
        call(delete("/api/drafts/{id}", draftId), minsu, null).andExpect(status().isNotFound());
        call(post("/api/drafts/{id}/publish", draftId), minsu, publishJson).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("빈 제목으로는 발행할 수 없고, 글은 임시저장 상태로 남는다")
    void publishRequiresTitle() throws Exception {
        Long draftId = createDraft(nayoung, "{\"content\":\"본문만\"}");

        call(post("/api/drafts/{id}/publish", draftId), nayoung, "{\"title\":\" \",\"content\":\"본문만\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"));

        assertThat(postRow(draftId)).containsEntry("status", "DRAFT");
    }

    @Test
    @DisplayName("발행하면 같은 id로 공개되고, 임시저장 목록에서 사라진다 (D-05)")
    void publish() throws Exception {
        Long draftId = createDraft(nayoung, "{\"content\":\"쓰다 만 글\"}");

        call(post("/api/drafts/{id}/publish", draftId), nayoung, """
                {"title":"완성한 글","content":"마지막으로 고친 본문"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(draftId))
                .andExpect(jsonPath("$.username").value("nayoung"));

        Map<String, Object> row = postRow(draftId);
        assertThat(row).containsEntry("status", "PUBLISHED");
        assertThat(row.get("published_at")).isNotNull();
        assertThat(row.get("edited_at")).isNull();

        mockMvc.perform(get("/api/blogs/nayoung/posts/{id}", draftId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("마지막으로 고친 본문"));
        mockMvc.perform(get("/api/posts"))
                .andExpect(jsonPath("$.content[*].title", contains("완성한 글")));
        call(get("/api/drafts"), nayoung, null).andExpect(jsonPath("$.totalElements").value(0));
        call(get("/api/drafts/{id}", draftId), nayoung, null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("삭제하면 204, 행이 사라진다")
    void deleteDraft() throws Exception {
        Long draftId = createDraft(nayoung, "{\"title\":\"버릴 글\",\"tags\":[\"Java\"]}");

        call(delete("/api/drafts/{id}", draftId), nayoung, null).andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM posts", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_tags", Long.class)).isZero();
    }

    @Test
    @DisplayName("길이 제한은 임시저장에도 그대로다 (제목 101자 → 400)")
    void lengthLimitStillApplies() throws Exception {
        call(post("/api/drafts"), nayoung, "{\"title\":\"%s\"}".formatted("가".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
    }

    @Test
    @DisplayName("발행 글은 임시저장 API로, 임시저장 글은 글 API로 다룰 수 없다 (각각 404)")
    void apisDoNotMix() throws Exception {
        Long draftId = createDraft(nayoung, "{\"title\":\"t\"}");
        Long publishedId = insertPost(nayoung.blogId(), defaultCategoryId(nayoung.blogId()), "PUBLISHED", "PUBLIC");

        call(put("/api/drafts/{id}", publishedId), nayoung, "{}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRAFT_NOT_FOUND"));
        call(put("/api/posts/{id}", draftId), nayoung, "{\"title\":\"t\",\"content\":\"c\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }
}
