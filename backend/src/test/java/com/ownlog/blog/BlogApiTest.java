package com.ownlog.blog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ownlog.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** API 5.1 블로그 홈, 5.2 블로그 수정 */
class BlogApiTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;

    TestUser nayoung;
    TestUser minsu;

    @BeforeEach
    void setUp() throws Exception {
        nayoung = signupAndLogin("nayoung");
        minsu = signupAndLogin("minsu");
    }

    private ResultActions updateBlog(TestUser requester, Long blogId, String name, String description) throws Exception {
        return mockMvc.perform(put("/api/blogs/{blogId}", blogId)
                .header(HttpHeaders.AUTHORIZATION, requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","description":"%s"}
                        """.formatted(name, description)));
    }

    @Test
    @DisplayName("방문자가 블로그 홈을 보면 isOwner는 false")
    void visitorHome() throws Exception {
        mockMvc.perform(get("/api/blogs/nayoung"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blogId").value(nayoung.blogId()))
                .andExpect(jsonPath("$.name").value("nayoung의 블로그"))
                .andExpect(jsonPath("$.owner.username").value("nayoung"))
                .andExpect(jsonPath("$.isOwner").value(false));
    }

    @Test
    @DisplayName("주인이 보면 isOwner는 true, 다른 회원이 보면 false")
    void ownerHome() throws Exception {
        mockMvc.perform(get("/api/blogs/nayoung").header(HttpHeaders.AUTHORIZATION, nayoung.bearer()))
                .andExpect(jsonPath("$.isOwner").value(true));
        mockMvc.perform(get("/api/blogs/nayoung").header(HttpHeaders.AUTHORIZATION, minsu.bearer()))
                .andExpect(jsonPath("$.isOwner").value(false));
    }

    @Test
    @DisplayName("없는 아이디의 블로그는 404 USER_NOT_FOUND")
    void unknownBlog() throws Exception {
        mockMvc.perform(get("/api/blogs/nobody"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("주인은 블로그 이름과 소개를 수정할 수 있다")
    void ownerUpdates() throws Exception {
        updateBlog(nayoung, nayoung.blogId(), "나영의 개발일지", "Spring과 React 공부 기록")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("나영의 개발일지"))
                .andExpect(jsonPath("$.description").value("Spring과 React 공부 기록"));

        mockMvc.perform(get("/api/blogs/nayoung"))
                .andExpect(jsonPath("$.name").value("나영의 개발일지"));
    }

    @Test
    @DisplayName("다른 회원의 블로그를 수정하면 403 FORBIDDEN")
    void otherUserCannotUpdate() throws Exception {
        updateBlog(minsu, nayoung.blogId(), "가로채기", "")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("이름을 비우면 400 INVALID_INPUT")
    void blankName() throws Exception {
        updateBlog(nayoung, nayoung.blogId(), " ", "소개")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }
}
