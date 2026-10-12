package com.ownlog.universe;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ownlog.IntegrationTest;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** API 14.2 기록 잔디, 6.4 날짜 필터 (AC-01 ~ AC-04) */
class ActivityApiTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;

    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    TestUser nayoung;
    TestUser minsu;
    Long category;
    LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        nayoung = signupAndLogin("nayoung");
        minsu = signupAndLogin("minsu");
        category = defaultCategoryId(nayoung.blogId());
        today = LocalDate.now(KST);
    }

    /** KST 날짜 + 시각(HH:mm)에 쓴 글 */
    private void postAt(LocalDate day, String time, String visibility) {
        jdbcTemplate.update("""
                INSERT INTO posts (blog_id, category_id, title, content, status, visibility, published_at, created_at, updated_at)
                VALUES (?, ?, ?, '본문', 'PUBLISHED', ?, (?::date + ?::time) AT TIME ZONE 'Asia/Seoul', now(), now())
                """, nayoung.blogId(), category, "글 " + day + " " + time, visibility, day.toString(), time);
    }

    private ResultActions activity(TestUser viewer) throws Exception {
        var request = get("/api/blogs/nayoung/activity");
        if (viewer != null) {
            request.header(HttpHeaders.AUTHORIZATION, viewer.bearer());
        }
        return mockMvc.perform(request).andExpect(status().isOk());
    }

    @Test
    @DisplayName("날짜는 KST 기준: 23:59와 다음 날 00:00은 다른 칸이다 (AC-04)")
    void groupsByKstDate() throws Exception {
        LocalDate d = today.minusDays(10);
        postAt(d, "23:59", "PUBLIC");
        postAt(d.plusDays(1), "00:00", "PUBLIC");
        postAt(d.plusDays(1), "09:00", "PUBLIC");

        activity(null)
                .andExpect(jsonPath("$.to").value(today.toString()))
                .andExpect(jsonPath("$.from").value(today.minusDays(364).toString()))
                .andExpect(jsonPath("$.days", hasSize(2)))
                .andExpect(jsonPath("$.days[0].date").value(d.toString()))
                .andExpect(jsonPath("$.days[0].count").value(1))
                .andExpect(jsonPath("$.days[1].count").value(2));
    }

    @Test
    @DisplayName("연속 기록: 오늘 안 썼어도 어제까지 이어졌으면 유지, 최장 기록은 따로 (AC-02)")
    void streaks() throws Exception {
        for (int i = 1; i <= 3; i++) {
            postAt(today.minusDays(i), "12:00", "PUBLIC");   // 어제부터 3일 연속
        }
        for (int i = 10; i <= 14; i++) {
            postAt(today.minusDays(i), "12:00", "PUBLIC");   // 예전에 5일 연속
        }
        activity(null).andExpect(jsonPath("$.currentStreak").value(3)).andExpect(jsonPath("$.longestStreak").value(5));

        postAt(today, "00:30", "PUBLIC");
        activity(null).andExpect(jsonPath("$.currentStreak").value(4));
    }

    @Test
    @DisplayName("하루 빠지면 연속 기록은 0, 1년보다 오래된 글은 세지 않는다")
    void brokenStreakAndOldPosts() throws Exception {
        postAt(today.minusDays(2), "12:00", "PUBLIC");
        postAt(today.minusDays(400), "12:00", "PUBLIC");
        activity(null).andExpect(jsonPath("$.currentStreak").value(0)).andExpect(jsonPath("$.days", hasSize(1)));
    }

    @Test
    @DisplayName("방문자는 비공개 글을 세지 않고, 주인은 센다")
    void privatePostsOnlyForOwner() throws Exception {
        postAt(today, "08:00", "PUBLIC");
        postAt(today, "09:00", "PRIVATE");
        activity(minsu).andExpect(jsonPath("$.days[0].count").value(1));
        activity(nayoung).andExpect(jsonPath("$.days[0].count").value(2));
    }

    @Test
    @DisplayName("블로그 글 목록 ?date= → 그날(KST) 쓴 글만, 형식이 틀리면 400 (AC-03)")
    void dateFilter() throws Exception {
        LocalDate d = today.minusDays(5);
        postAt(d, "00:00", "PUBLIC");
        postAt(d, "23:59", "PUBLIC");
        postAt(d.plusDays(1), "00:00", "PUBLIC");

        mockMvc.perform(get("/api/blogs/nayoung/posts").param("date", d.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/blogs/nayoung/posts"))
                .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get("/api/blogs/nayoung/posts").param("date", "2026-13-40"))
                .andExpect(status().isBadRequest());
    }
}
