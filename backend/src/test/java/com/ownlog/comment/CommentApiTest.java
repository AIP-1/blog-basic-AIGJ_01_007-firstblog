package com.ownlog.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ownlog.IntegrationTest;
import com.ownlog.user.domain.User;
import com.ownlog.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** API 8.1 ~ 8.4 댓글. 나영의 글에 민수·지수가 댓글을 달아요. */
class CommentApiTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired EntityManagerFactory entityManagerFactory;

    TestUser nayoung;   // 글 주인
    TestUser minsu;
    TestUser jisoo;
    Long postId;

    @BeforeEach
    void setUp() throws Exception {
        nayoung = signupAndLogin("nayoung");
        minsu = signupAndLogin("minsu");
        jisoo = signupAndLogin("jisoo");
        postId = insertPost(nayoung.blogId(), defaultCategoryId(nayoung.blogId()), "PUBLISHED", "PUBLIC");
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, TestUser user, String json) throws Exception {
        if (user != null) {
            request.header(HttpHeaders.AUTHORIZATION, user.bearer());
        }
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    private Long write(TestUser user, Long targetPostId, String content, Long parentId) throws Exception {
        String json = parentId == null
                ? "{\"content\":\"%s\"}".formatted(content)
                : "{\"content\":\"%s\",\"parentId\":%d}".formatted(content, parentId);
        String body = perform(post("/api/posts/{id}/comments", targetPostId), user, json)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions list(TestUser viewer) throws Exception {
        return perform(get("/api/posts/{id}/comments", postId), viewer, null);
    }

    private ResultActions deleteComment(TestUser user, Long commentId) throws Exception {
        return perform(delete("/api/comments/{id}", commentId), user, null);
    }

    @Test
    @DisplayName("부모 → 답글 순서로 묶이고, 답글에는 replies 필드가 없다")
    void listStructure() throws Exception {
        Long first = write(minsu, postId, "첫 댓글", null);
        Long second = write(jisoo, postId, "두 번째 댓글", null);
        write(nayoung, postId, "첫 댓글의 답글", first);
        write(jisoo, postId, "첫 댓글의 두 번째 답글", first);

        list(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(4))
                .andExpect(jsonPath("$.comments[*].id", contains(first.intValue(), second.intValue())))
                .andExpect(jsonPath("$.comments[0].replies[*].content", contains("첫 댓글의 답글", "첫 댓글의 두 번째 답글")))
                .andExpect(jsonPath("$.comments[0].replies[0].replies").doesNotExist())
                .andExpect(jsonPath("$.comments[1].replies", empty()))
                .andExpect(jsonPath("$.comments[0].author.username").value("minsu"))
                .andExpect(jsonPath("$.comments[0].isEdited").value(false));
    }

    @Test
    @DisplayName("버튼: 방문자는 없음, 작성자는 수정·삭제, 글 주인은 남의 댓글 삭제, 다른 회원은 신고")
    void buttonsByViewer() throws Exception {
        write(minsu, postId, "민수 댓글", null);

        list(null).andExpect(jsonPath("$.comments[0].canEdit").value(false))
                .andExpect(jsonPath("$.comments[0].canDelete").value(false))
                .andExpect(jsonPath("$.comments[0].canReport").value(false));
        list(minsu).andExpect(jsonPath("$.comments[0].canEdit").value(true))
                .andExpect(jsonPath("$.comments[0].canDelete").value(true))
                .andExpect(jsonPath("$.comments[0].canReport").value(false));
        list(nayoung).andExpect(jsonPath("$.comments[0].canEdit").value(false))
                .andExpect(jsonPath("$.comments[0].canDelete").value(true))
                .andExpect(jsonPath("$.comments[0].canReport").value(true));
        list(jisoo).andExpect(jsonPath("$.comments[0].canEdit").value(false))
                .andExpect(jsonPath("$.comments[0].canDelete").value(false))
                .andExpect(jsonPath("$.comments[0].canReport").value(true));
    }

    @Test
    @DisplayName("답글의 답글, 다른 글의 댓글, 삭제 표시된 댓글에는 답글을 달 수 없다")
    void invalidParent() throws Exception {
        Long parent = write(minsu, postId, "부모", null);
        Long reply = write(jisoo, postId, "답글", parent);
        Long otherPost = insertPost(nayoung.blogId(), defaultCategoryId(nayoung.blogId()), "PUBLISHED", "PUBLIC");
        Long otherPostComment = write(minsu, otherPost, "다른 글 댓글", null);

        for (Long badParent : new Long[]{reply, otherPostComment}) {
            perform(post("/api/posts/{id}/comments", postId), minsu,
                    "{\"content\":\"x\",\"parentId\":%d}".formatted(badParent))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARENT_COMMENT"));
        }

        deleteComment(minsu, parent).andExpect(status().isNoContent());   // 답글이 있어서 삭제 표시
        perform(post("/api/posts/{id}/comments", postId), minsu, "{\"content\":\"x\",\"parentId\":%d}".formatted(parent))
                .andExpect(jsonPath("$.code").value("INVALID_PARENT_COMMENT"));
    }

    @Test
    @DisplayName("방문자는 댓글을 쓸 수 없고(401), 비공개 글의 댓글은 404")
    void accessRules() throws Exception {
        perform(post("/api/posts/{id}/comments", postId), null, "{\"content\":\"x\"}")
                .andExpect(status().isUnauthorized());

        Long privatePost = insertPost(nayoung.blogId(), defaultCategoryId(nayoung.blogId()), "PUBLISHED", "PRIVATE");
        perform(get("/api/posts/{id}/comments", privatePost), null, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        perform(post("/api/posts/{id}/comments", privatePost), minsu, "{\"content\":\"x\"}")
                .andExpect(status().isNotFound());
        perform(get("/api/posts/{id}/comments", privatePost), nayoung, null).andExpect(status().isOk());
    }

    @Test
    @DisplayName("작성자는 수정할 수 있고(수정됨 표시), 글 주인이라도 남의 댓글은 수정할 수 없다(403)")
    void update() throws Exception {
        Long commentId = write(minsu, postId, "처음", null);

        perform(patch("/api/comments/{id}", commentId), minsu, "{\"content\":\"고친 댓글\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("고친 댓글"))
                .andExpect(jsonPath("$.isEdited").value(true));
        perform(patch("/api/comments/{id}", commentId), nayoung, "{\"content\":\"가로채기\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("답글이 있는 댓글을 지우면 \"삭제된 댓글입니다\", 마지막 답글까지 지우면 부모도 사라진다")
    void softDeleteThenCleanUp() throws Exception {
        Long parent = write(minsu, postId, "부모", null);
        Long reply = write(jisoo, postId, "답글", parent);

        deleteComment(minsu, parent).andExpect(status().isNoContent());
        list(null)
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.comments[0].isDeleted").value(true))
                .andExpect(jsonPath("$.comments[0].content").value(nullValue()))
                .andExpect(jsonPath("$.comments[0].author").value(nullValue()))
                .andExpect(jsonPath("$.comments[0].replies[0].content").value("답글"));

        deleteComment(jisoo, reply).andExpect(status().isNoContent());
        list(null)
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.comments", empty()));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM comments", Long.class)).isZero();
    }

    @Test
    @DisplayName("글 주인은 남의 댓글을 지울 수 있고, 관계없는 회원은 403")
    void deletePermission() throws Exception {
        Long byMinsu = write(minsu, postId, "스팸", null);
        Long byJisoo = write(jisoo, postId, "지수 댓글", null);

        deleteComment(jisoo, byMinsu).andExpect(status().isForbidden());
        deleteComment(nayoung, byMinsu).andExpect(status().isNoContent());
        deleteComment(nayoung, byJisoo).andExpect(status().isNoContent());
        deleteComment(nayoung, byJisoo).andExpect(status().isNotFound());   // 이미 지운 댓글
    }

    @Test
    @DisplayName("실제로 지우면 신고 기록도 지워지고, 삭제 표시만 하면 신고 기록이 남는다")
    void reportsOnDelete() throws Exception {
        Long lonely = write(minsu, postId, "답글 없는 댓글", null);
        Long parent = write(minsu, postId, "답글 있는 댓글", null);
        write(jisoo, postId, "답글", parent);
        String report = "INSERT INTO reports (comment_id, reporter_id, reason, created_at) VALUES (?, ?, 'SPAM', now())";
        jdbcTemplate.update(report, lonely, jisoo.userId());
        jdbcTemplate.update(report, parent, jisoo.userId());

        deleteComment(minsu, lonely).andExpect(status().isNoContent());
        deleteComment(minsu, parent).andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForList("SELECT comment_id FROM reports", Long.class)).containsExactly(parent);
    }

    @Test
    @DisplayName("탈퇴한 회원의 댓글은 \"탈퇴한 사용자\"로 보인다")
    void withdrawnAuthor() throws Exception {
        write(minsu, postId, "탈퇴 전에 쓴 댓글", null);
        User user = userRepository.findById(minsu.userId()).orElseThrow();
        user.withdraw("random-hash");
        userRepository.save(user);

        list(null)
                .andExpect(jsonPath("$.comments[0].content").value("탈퇴 전에 쓴 댓글"))
                .andExpect(jsonPath("$.comments[0].author.nickname").value("탈퇴한 사용자"))
                .andExpect(jsonPath("$.comments[0].author.username").value(nullValue()));
    }

    @Test
    @DisplayName("N+1 없음: 3명이 쓴 댓글 6개를 조회해도 SQL 5개 이하")
    void noNPlusOne() throws Exception {
        for (TestUser user : new TestUser[]{nayoung, minsu, jisoo}) {
            Long parent = write(user, postId, "댓글", null);
            write(user, postId, "답글", parent);
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        list(null).andExpect(jsonPath("$.totalCount").value(6));

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(5);
    }
}
