package com.ownlog.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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

/** 받는 사람이 있는 타임캡슐 (TC-07 ~ TC-10, API 12.3) */
class RecipientCapsuleApiTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;

    TestUser nayoung;   // 보낸 사람
    TestUser minsu;     // 받는 사람
    TestUser jiwon;     // 제3자

    @BeforeEach
    void setUp() throws Exception {
        nayoung = signupAndLogin("nayoung");
        minsu = signupAndLogin("minsu");
        jiwon = signupAndLogin("jiwon");
    }

    private static String inFuture(Duration d) {
        return OffsetDateTime.now(ZoneOffset.ofHours(9)).plus(d).toString();
    }

    private ResultActions publish(String extra) throws Exception {
        return mockMvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"민수에게\",\"content\":\"편지 본문\",\"tags\":[\"편지\"]" + extra + "}"));
    }

    private Long sendToMinsu() throws Exception {
        String body = publish(",\"openAt\":\"%s\",\"recipientUsername\":\"@minsu\",\"visibility\":\"PUBLIC\""
                .formatted(inFuture(Duration.ofHours(1))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions getAs(String url, TestUser viewer) throws Exception {
        var request = get(url);
        if (viewer != null) {
            request.header(HttpHeaders.AUTHORIZATION, viewer.bearer());
        }
        return mockMvc.perform(request);
    }

    private void open(Long id) {
        jdbcTemplate.update("UPDATE posts SET sealed_at = now() - interval '2 days', published_at = now() - interval '1 minute' WHERE id = ?", id);
    }

    @Test
    @DisplayName("받는 사람은 타임캡슐에만, 존재하는 다른 회원만 (TC-07)")
    void validatesRecipient() throws Exception {
        publish(",\"recipientUsername\":\"minsu\"").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECIPIENT_REQUIRES_CAPSULE"));
        String openAt = inFuture(Duration.ofHours(1));
        for (String who : new String[] {"nobody", "nayoung", "@NAYOUNG"}) {
            publish(",\"openAt\":\"%s\",\"recipientUsername\":\"%s\"".formatted(openAt, who))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RECIPIENT"));
        }
    }

    @Test
    @DisplayName("공개로 보내도 항상 비공개: 공개 목록·검색·블로그 목록·12.1에 없다")
    void alwaysPrivate() throws Exception {
        Long id = sendToMinsu();
        open(id);
        assertThat(jdbcTemplate.queryForObject("SELECT visibility FROM posts WHERE id = ?", String.class, id)).isEqualTo("PRIVATE");
        getAs("/api/posts", null).andExpect(jsonPath("$.totalElements").value(0));
        getAs("/api/blogs/nayoung/posts", minsu).andExpect(jsonPath("$.totalElements").value(0));
        getAs("/api/universe", null).andExpect(jsonPath("$.nodes", hasSize(0)));
    }

    @Test
    @DisplayName("봉인 중: 받는 사람은 잠금 카드(보낸 사람·받는 사람 표시), 제3자·비로그인은 404 (TC-08)")
    void sealedForRecipientOnly() throws Exception {
        Long id = sendToMinsu();
        getAs("/api/blogs/nayoung/posts/" + id, minsu)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(nullValue()))
                .andExpect(jsonPath("$.timeCapsule.locked").value(true))
                .andExpect(jsonPath("$.author.username").value("nayoung"))
                .andExpect(jsonPath("$.recipient.username").value("minsu"));
        getAs("/api/blogs/nayoung/posts/" + id, jiwon).andExpect(status().isNotFound());
        getAs("/api/blogs/nayoung/posts/" + id, null).andExpect(status().isNotFound());
        getAs("/api/blogs/nayoung/posts/" + id, nayoung)
                .andExpect(jsonPath("$.content").value("편지 본문"))
                .andExpect(jsonPath("$.recipient.nickname").value("minsu"));
        mockMvc.perform(post("/api/posts/{id}/comments", id).header(HttpHeaders.AUTHORIZATION, minsu.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"아직?\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("열린 뒤: 받는 사람은 본문을 보고 댓글을 달 수 있고, 제3자는 여전히 404 (TC-08)")
    void openedForRecipientOnly() throws Exception {
        Long id = sendToMinsu();
        open(id);
        getAs("/api/blogs/nayoung/posts/" + id, minsu)
                .andExpect(jsonPath("$.content").value("편지 본문"))
                .andExpect(jsonPath("$.timeCapsule.locked").value(false));
        mockMvc.perform(post("/api/posts/{id}/comments", id).header(HttpHeaders.AUTHORIZATION, minsu.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"고마워!\"}"))
                .andExpect(status().isCreated());
        getAs("/api/blogs/nayoung/posts/" + id, jiwon).andExpect(status().isNotFound());
        getAs("/api/posts/" + id + "/comments", jiwon).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("받은 타임캡슐 목록: 안 열린 것(개봉 빠른 순) → 열린 것, 보낸 사람 정보, 나에게 온 것만 (TC-09, 12.3)")
    void inbox() throws Exception {
        Long opened = sendToMinsu();
        open(opened);
        publish(",\"openAt\":\"%s\",\"recipientUsername\":\"minsu\"".formatted(inFuture(Duration.ofDays(30)))).andExpect(status().isCreated());
        publish(",\"openAt\":\"%s\",\"recipientUsername\":\"minsu\"".formatted(inFuture(Duration.ofDays(2)))).andExpect(status().isCreated());
        publish(",\"openAt\":\"%s\",\"recipientUsername\":\"jiwon\"".formatted(inFuture(Duration.ofDays(1)))).andExpect(status().isCreated());

        getAs("/api/users/me/time-capsules", minsu)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].opened").value(false))
                .andExpect(jsonPath("$[0].sender.username").value("nayoung"))
                .andExpect(jsonPath("$[1].opened").value(false))
                .andExpect(jsonPath("$[2].opened").value(true))
                .andExpect(jsonPath("$[2].id").value(opened));
        getAs("/api/users/me/time-capsules", jiwon).andExpect(jsonPath("$", hasSize(1)));
        getAs("/api/users/me/time-capsules", null).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("열린 뒤 보낸 사람이 공개로 바꾸려 해도 비공개 유지")
    void staysPrivateAfterEdit() throws Exception {
        Long id = sendToMinsu();
        open(id);
        mockMvc.perform(put("/api/posts/{id}", id).header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"t\",\"content\":\"c\",\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isOk());
        getAs("/api/blogs/nayoung/posts/" + id, jiwon).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("임시저장 글도 받는 사람 캡슐로 발행할 수 있다")
    void draftToRecipient() throws Exception {
        String body = mockMvc.perform(post("/api/drafts").header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"초안\"}"))
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        mockMvc.perform(post("/api/drafts/{id}/publish", id).header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title":"초안","content":"c","openAt":"%s","recipientUsername":"minsu"}
                                """.formatted(inFuture(Duration.ofHours(3)))))
                .andExpect(status().isOk());
        getAs("/api/users/me/time-capsules", minsu).andExpect(jsonPath("$", hasSize(1)));
    }
}
