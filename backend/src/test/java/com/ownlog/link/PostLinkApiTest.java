package com.ownlog.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** 글 연결 [[번호|글자]]와 백링크 (LK-01 ~ LK-05, API 13장) */
class PostLinkApiTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;

    TestUser nayoung;
    TestUser minsu;

    @BeforeEach
    void setUp() throws Exception {
        nayoung = signupAndLogin("nayoung");
        minsu = signupAndLogin("minsu");
    }

    private Long publish(TestUser user, String title, String content, String extra) throws Exception {
        String json = """
                {"title":"%s","content":"%s"%s}
                """.formatted(title, content, extra);
        String body = mockMvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private Long publish(TestUser user, String title, String content) throws Exception {
        return publish(user, title, content, "");
    }

    private ResultActions getAs(String url, TestUser viewer) throws Exception {
        var request = get(url);
        if (viewer != null) {
            request.header(HttpHeaders.AUTHORIZATION, viewer.bearer());
        }
        return mockMvc.perform(request);
    }

    private int linkCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM post_links", Integer.class);
    }

    @Test
    @DisplayName("발행하면 본문의 링크가 저장되고, 대상 글 백링크에 나온다 (LK-03, LK-04)")
    void publishStoresLinksAndBacklinks() throws Exception {
        Long jwt = publish(nayoung, "JWT 정리", "본문");
        Long security = publish(nayoung, "Security 적용기", "[[" + jwt + "|JWT 정리]]를 참고했어요");

        getAs("/api/posts/" + jwt + "/backlinks", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(security))
                .andExpect(jsonPath("$[0].title").value("Security 적용기"))
                .andExpect(jsonPath("$[0].username").value("nayoung"));

        getAs("/api/blogs/nayoung/posts/" + security, null)
                .andExpect(jsonPath("$.linkedPosts['" + jwt + "'].accessible").value(true))
                .andExpect(jsonPath("$.linkedPosts['" + jwt + "'].title").value("JWT 정리"))
                .andExpect(jsonPath("$.linkedPosts['" + jwt + "'].username").value("nayoung"))
                // 미리보기 카드용 (LK-06)
                .andExpect(jsonPath("$.linkedPosts['" + jwt + "'].nickname").value("nayoung"))
                .andExpect(jsonPath("$.linkedPosts['" + jwt + "'].summary").value("본문"))
                .andExpect(jsonPath("$.linkedPosts['" + jwt + "'].publishedAt").isNotEmpty());
    }

    @Test
    @DisplayName("수정하면 링크를 다시 계산하고, 자기 자신·없는 글·임시저장 글은 저장하지 않는다")
    void editRecalculatesAndIgnoresInvalid() throws Exception {
        Long a = publish(nayoung, "A", "본문");
        Long b = publish(nayoung, "B", "본문");
        String draft = mockMvc.perform(post("/api/drafts").header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"초안\"}"))
                .andReturn().getResponse().getContentAsString();
        Long draftId = ((Number) JsonPath.read(draft, "$.id")).longValue();

        Long c = publish(nayoung, "C", "[[" + a + "|A]] [[999999|없음]] [[" + draftId + "|초안]]");
        assertThat(linkCount()).isEqualTo(1);

        mockMvc.perform(put("/api/posts/{id}", c).header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"C\",\"content\":\"[[" + b + "|B]] [[" + c + "|나]]\"}"))
                .andExpect(status().isOk());

        Long target = jdbcTemplate.queryForObject("SELECT target_post_id FROM post_links", Long.class);
        assertThat(linkCount()).isEqualTo(1);
        assertThat(target).isEqualTo(b);
        getAs("/api/blogs/nayoung/posts/" + c, null)
                .andExpect(jsonPath("$.linkedPosts['" + c + "'].accessible").value(true));   // 화면용 정보는 자기 자신도 링크로 보여줘요
    }

    @Test
    @DisplayName("볼 수 없는 대상(비공개, 봉인, 삭제)은 accessible=false이고 제목을 주지 않는다 (LK-02)")
    void inaccessibleTargetsHideTitle() throws Exception {
        Long secret = publish(minsu, "민수의 비밀", "본문", ",\"visibility\":\"PRIVATE\"");
        String openAt = OffsetDateTime.now(ZoneOffset.ofHours(9)).plusDays(1).toString();
        Long capsule = publish(minsu, "민수의 캡슐", "본문", ",\"openAt\":\"" + openAt + "\"");
        Long gone = publish(minsu, "곧 삭제", "본문");
        Long mine = publish(nayoung, "내 글",
                "[[" + secret + "|비밀]] [[" + capsule + "|캡슐]] [[" + gone + "|삭제]]");
        mockMvc.perform(delete("/api/posts/{id}", gone).header(HttpHeaders.AUTHORIZATION, minsu.bearer()))
                .andExpect(status().isNoContent());

        getAs("/api/blogs/nayoung/posts/" + mine, nayoung)
                .andExpect(jsonPath("$.linkedPosts['" + secret + "'].accessible").value(false))
                .andExpect(jsonPath("$.linkedPosts['" + secret + "'].title").doesNotExist())
                .andExpect(jsonPath("$.linkedPosts['" + secret + "'].summary").doesNotExist())
                .andExpect(jsonPath("$.linkedPosts['" + capsule + "'].accessible").value(false))
                .andExpect(jsonPath("$.linkedPosts['" + gone + "'].accessible").value(false));

        // 링크를 건 민수 본인에게는 자기 비공개 글이 보여요
        getAs("/api/blogs/nayoung/posts/" + mine, minsu)
                .andExpect(jsonPath("$.linkedPosts['" + secret + "'].accessible").value(true));
        // 삭제한 글의 링크는 지워졌어요
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM post_links WHERE target_post_id = ?",
                Integer.class, gone)).isZero();
    }

    @Test
    @DisplayName("백링크는 보는 사람이 볼 수 있는 글만, 대상 글을 볼 수 없으면 404 (LK-03, LK-05)")
    void backlinksRespectVisibility() throws Exception {
        Long target = publish(nayoung, "공개 대상", "본문");
        publish(minsu, "민수 공개 글", "[[" + target + "|대상]]");
        publish(minsu, "민수 비공개 글", "[[" + target + "|대상]]", ",\"visibility\":\"PRIVATE\"");

        getAs("/api/posts/" + target + "/backlinks", null).andExpect(jsonPath("$", hasSize(1)));
        getAs("/api/posts/" + target + "/backlinks", nayoung).andExpect(jsonPath("$", hasSize(1)));
        getAs("/api/posts/" + target + "/backlinks", minsu).andExpect(jsonPath("$", hasSize(2)));   // 자기 비공개 글 포함

        Long hidden = publish(nayoung, "비공개 대상", "본문", ",\"visibility\":\"PRIVATE\"");
        getAs("/api/posts/" + hidden + "/backlinks", minsu).andExpect(status().isNotFound());
        getAs("/api/posts/" + hidden + "/backlinks", nayoung).andExpect(status().isOk());
    }

    @Test
    @DisplayName("자동완성: 내 글 먼저(비공개 포함), 검색어가 있으면 다른 사람 공개 글도, 로그인 필요 (13.2)")
    void suggestions() throws Exception {
        publish(nayoung, "스프링 입문", "본문");
        publish(nayoung, "스프링 비밀 메모", "본문", ",\"visibility\":\"PRIVATE\"");
        publish(minsu, "민수의 스프링", "본문");
        publish(minsu, "민수의 비공개 스프링", "본문", ",\"visibility\":\"PRIVATE\"");

        getAs("/api/posts/link-suggestions?q=스프링", nayoung)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].username").value("nayoung"))
                .andExpect(jsonPath("$[1].username").value("nayoung"))
                .andExpect(jsonPath("$[2].title").value("민수의 스프링"));

        getAs("/api/posts/link-suggestions", nayoung).andExpect(jsonPath("$", hasSize(2)));   // 검색어 없으면 내 최근 글만
        getAs("/api/posts/link-suggestions?q=%25", nayoung).andExpect(jsonPath("$", hasSize(0)));   // % 는 글자 그대로
        getAs("/api/posts/link-suggestions?q=스프링", null).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("탈퇴하면 내 글이 출발점이거나 대상인 링크가 모두 지워진다")
    void withdrawalRemovesLinks() throws Exception {
        Long mine = publish(nayoung, "내 글", "본문");
        Long other = publish(minsu, "민수 글", "[[" + mine + "|나영 글]]");
        publish(nayoung, "내 다른 글", "[[" + other + "|민수 글]]");
        assertThat(linkCount()).isEqualTo(2);

        mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"ownlog1234\"}"))
                .andExpect(status().isNoContent());

        assertThat(linkCount()).isZero();
    }
}
