package com.ownlog.universe;

import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
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

/** API 14.1 기록 그래프, 14.3 서비스 전체 우주 (GR-01 ~ GR-07). 관련 있는 글 계산 자체는 RelatednessTest */
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
    @DisplayName("별은 글, 선은 [[링크]](방향)와 관련 있는 글(RELATED: 관련도·근거 태그), 연결 수와 토픽이 있다")
    void nodesAndEdges() throws Exception {
        Long jwt = publish(nayoung, """
                {"title":"JWT 인증 정리","content":"c","topicId":1,"tags":["Spring","JWT","보안"]}""");
        Long sec = publish(nayoung, """
                {"title":"Spring Security 적용기","content":"[[%d|JWT]]","tags":["Spring","JWT","보안"]}""".formatted(jwt));
        Long filter = publish(nayoung, """
                {"title":"JWT 필터 체인","content":"c","tags":["JWT","보안"]}""");
        Long diary = publish(nayoung, """
                {"title":"일기","content":"c","tags":["일상"]}""");
        publish(nayoung, """
                {"title":"점심 메뉴","content":"c","tags":["음식"]}""");
        publish(nayoung, """
                {"title":"주말 산책","content":"c","tags":["산책"]}""");

        graph(null)
                .andExpect(jsonPath("$.nodes", hasSize(6)))
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.edges[?(@.type == 'LINK')]", hasSize(1)))
                .andExpect(jsonPath("$.edges[?(@.type == 'LINK')].source").value(sec.intValue()))
                .andExpect(jsonPath("$.edges[?(@.type == 'LINK')].target").value(jwt.intValue()))
                // [[링크]]로 이어진 JWT ~ Security는 관련 선을 또 긋지 않고, 필터 글이 두 글과 이어져요
                .andExpect(jsonPath("$.edges[?(@.type == 'RELATED')]", hasSize(2)))
                .andExpect(jsonPath("$.edges[?(@.type == 'RELATED' && @.source == %d && @.target == %d)].tags[*]"
                        .formatted(jwt, filter), containsInAnyOrder("JWT", "보안")))
                .andExpect(jsonPath("$.edges[?(@.type == 'RELATED' && @.source == %d && @.target == %d)].weight"
                        .formatted(sec, filter), everyItem(both(greaterThanOrEqualTo(0.3)).and(lessThanOrEqualTo(1.0)))))
                .andExpect(jsonPath("$.edges[?(@.source == %d || @.target == %d)]".formatted(diary, diary), hasSize(0)))
                .andExpect(jsonPath("$.nodes[?(@.id == %d)].linkCount".formatted(jwt)).value(1))
                .andExpect(jsonPath("$.nodes[?(@.id == %d)].topic".formatted(jwt)).value("it"))
                .andExpect(jsonPath("$.nodes[?(@.id == %d)].tags[*]".formatted(jwt), hasSize(3)));
    }

    @Test
    @DisplayName("거의 모든 글에 붙은 흔한 태그 하나로는 선이 없고, 드문 태그를 함께 쓴 글만 이어진다")
    void commonTagDoesNotConnect() throws Exception {
        for (String place : new String[] {"강릉", "속초", "경주", "전주", "여수", "통영", "안동", "춘천"}) {
            publish(nayoung, """
                    {"title":"%s 다녀옴","content":"c","tags":["여행"]}""".formatted(place));
        }
        Long sea = publish(nayoung, """
                {"title":"바다 사진","content":"c","tags":["여행","필름카메라"]}""");
        Long alley = publish(nayoung, """
                {"title":"골목 사진","content":"c","tags":["여행","필름카메라"]}""");

        // 예전에는 "여행"을 공유한 45쌍이 모두 이어졌어요
        graph(null)
                .andExpect(jsonPath("$.nodes", hasSize(10)))
                .andExpect(jsonPath("$.edges", hasSize(1)))
                .andExpect(jsonPath("$.edges[0].type").value("RELATED"))
                .andExpect(jsonPath("$.edges[0].source").value(sea.intValue()))
                .andExpect(jsonPath("$.edges[0].target").value(alley.intValue()))
                .andExpect(jsonPath("$.edges[0].tags", contains("필름카메라")));
    }

    @Test
    @DisplayName("방문자에게 비공개 글은 별로 나오지 않고 그 선도 없다, 주인에게는 나온다")
    void privatePostsOnlyForOwner() throws Exception {
        Long open = publish(nayoung, """
                {"title":"제주 올레길 7코스","content":"c","tags":["올레길"]}""");
        publish(nayoung, """
                {"title":"비공개","content":"[[%d|공개]]","visibility":"PRIVATE","tags":["a"]}""".formatted(open));
        Long hidden = publish(nayoung, """
                {"title":"제주 올레길 메모","content":"c","visibility":"PRIVATE","tags":["올레길"]}""");

        graph(minsu).andExpect(jsonPath("$.nodes", hasSize(1))).andExpect(jsonPath("$.edges", hasSize(0)));
        graph(null).andExpect(jsonPath("$.nodes", hasSize(1))).andExpect(jsonPath("$.edges", hasSize(0)));
        graph(nayoung).andExpect(jsonPath("$.nodes", hasSize(3)))
                .andExpect(jsonPath("$.edges[?(@.type == 'LINK')]", hasSize(1)))
                .andExpect(jsonPath("$.edges[?(@.type == 'RELATED')].target").value(hidden.intValue()));
    }

    @Test
    @DisplayName("방문자에게 봉인 캡슐은 제목·개봉 시각만 있는 잠긴 별이고 선이 없다 (관련 선 계산에도 쓰지 않는다)")
    void sealedCapsuleIsLonelyLockedStar() throws Exception {
        Long open = publish(nayoung, """
                {"title":"비밀 정원 산책","content":"c","tags":["정원"]}""");
        Long photo = publish(nayoung, """
                {"title":"비밀 정원 사진","content":"c","tags":["정원"]}""");
        String openAt = OffsetDateTime.now(ZoneOffset.ofHours(9)).plusDays(3).toString();
        Long capsule = publish(nayoung, """
                {"title":"비밀 정원 산책 두 번째","content":"[[%d|공개]]","topicId":2,"tags":["정원"],"openAt":"%s"}"""
                .formatted(open, openAt));

        graph(minsu)
                .andExpect(jsonPath("$.nodes", hasSize(3)))
                .andExpect(jsonPath("$.edges[?(@.source == %d || @.target == %d)]".formatted(capsule, capsule), hasSize(0)))
                .andExpect(jsonPath("$.edges", hasSize(1)))
                .andExpect(jsonPath("$.edges[0].type").value("RELATED"))
                .andExpect(jsonPath("$.nodes[?(@.id == %d)].locked".formatted(capsule)).value(true))
                .andExpect(jsonPath("$.nodes[?(@.id == %d)].title".formatted(capsule)).value("비밀 정원 산책 두 번째"))
                .andExpect(jsonPath("$.nodes[?(@.id == %d)].openAt".formatted(capsule)).isNotEmpty())
                .andExpect(jsonPath("$.nodes[?(@.id == %d)].topic".formatted(capsule)).doesNotExist())
                .andExpect(jsonPath("$.nodes[?(@.id == %d)].tags".formatted(capsule)).doesNotExist());

        // 주인에게는 캡슐도 이어져요: 캡슐 → 산책 [[링크]], 산책 ~ 사진, 사진 ~ 캡슐 관련 선
        graph(nayoung)
                .andExpect(jsonPath("$.edges[?(@.type == 'LINK')].source").value(capsule.intValue()))
                .andExpect(jsonPath("$.edges[?(@.type == 'RELATED')]", hasSize(2)))
                .andExpect(jsonPath("$.edges[?(@.type == 'RELATED' && @.source == %d)].target".formatted(photo))
                        .value(capsule.intValue()));
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
                .andExpect(jsonPath("$.edges", hasSize(1)))
                .andExpect(jsonPath("$.edges[?(@.type == 'LINK')].target").value(mine.intValue()));
    }

    @Test
    @DisplayName("서비스 전체 우주: 블로그가 달라도 관련 있는 공개 글끼리 이어지고, 비공개 글은 계산에 들어가지 않는다")
    void universeRelatedAcrossBlogs() throws Exception {
        Long mine = publish(nayoung, """
                {"title":"제주 올레길 7코스","content":"바다를 따라 걸었어요","tags":["올레길"]}""");
        Long his = publish(minsu, """
                {"title":"제주 올레길 걷기","content":"c","tags":["올레길"]}""");
        Long hidden = publish(minsu, """
                {"title":"제주 올레길 비공개","content":"c","visibility":"PRIVATE","tags":["올레길"]}""");
        publish(minsu, """
                {"title":"점심 메뉴","content":"c","tags":["음식"]}""");

        mockMvc.perform(get("/api/universe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes", hasSize(3)))
                .andExpect(jsonPath("$.edges", hasSize(1)))
                .andExpect(jsonPath("$.edges[0].type").value("RELATED"))
                .andExpect(jsonPath("$.edges[0].source").value(mine.intValue()))
                .andExpect(jsonPath("$.edges[0].target").value(his.intValue()))
                .andExpect(jsonPath("$.edges[0].tags", contains("올레길")))
                .andExpect(jsonPath("$.edges[?(@.source == %d || @.target == %d)]".formatted(hidden, hidden), hasSize(0)));
    }

    @Test
    @DisplayName("글이 없으면 빈 우주, 없는 블로그는 404")
    void emptyAndNotFound() throws Exception {
        graph(null).andExpect(jsonPath("$.nodes", hasSize(0))).andExpect(jsonPath("$.edges", hasSize(0)));
        mockMvc.perform(get("/api/blogs/nobody/graph")).andExpect(status().isNotFound());
    }
}
