package com.ownlog.reaction;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** API 9.1 좋아요, 9.2 북마크, 4.8 내 북마크 목록 */
class ReactionApiTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;

    TestUser nayoung;
    TestUser minsu;
    Long postId;

    @BeforeEach
    void setUp() throws Exception {
        nayoung = signupAndLogin("nayoung");
        minsu = signupAndLogin("minsu");
        postId = insertPost(nayoung.blogId(), defaultCategoryId(nayoung.blogId()), "PUBLISHED", "PUBLIC");
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, TestUser user) throws Exception {
        if (user != null) {
            request.header(HttpHeaders.AUTHORIZATION, user.bearer());
        }
        return mockMvc.perform(request);
    }

    @Test
    @DisplayName("좋아요는 멱등하다: 누르기 두 번 → 1, 취소 두 번 → 0")
    void likeIsIdempotent() throws Exception {
        perform(put("/api/posts/{id}/like", postId), minsu)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));
        perform(put("/api/posts/{id}/like", postId), minsu)
                .andExpect(jsonPath("$.likeCount").value(1));

        perform(delete("/api/posts/{id}/like", postId), minsu)
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(0));
        perform(delete("/api/posts/{id}/like", postId), minsu)
                .andExpect(jsonPath("$.likeCount").value(0));
    }

    @Test
    @DisplayName("본인 글도 좋아요할 수 있고, 글 상세에 반영된다 (C-03)")
    void likeOwnPost() throws Exception {
        perform(put("/api/posts/{id}/like", postId), nayoung).andExpect(jsonPath("$.likeCount").value(1));
        perform(put("/api/posts/{id}/like", postId), minsu).andExpect(jsonPath("$.likeCount").value(2));

        perform(get("/api/blogs/nayoung/posts/{id}", postId), minsu)
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(2));
    }

    @Test
    @DisplayName("방문자는 401, 남의 비공개 글은 404")
    void likeAccess() throws Exception {
        perform(put("/api/posts/{id}/like", postId), null).andExpect(status().isUnauthorized());

        Long privatePost = insertPost(nayoung.blogId(), defaultCategoryId(nayoung.blogId()), "PUBLISHED", "PRIVATE");
        perform(put("/api/posts/{id}/like", privatePost), minsu)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        perform(put("/api/posts/{id}/bookmark", privatePost), minsu).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("북마크도 멱등하다")
    void bookmarkIsIdempotent() throws Exception {
        perform(put("/api/posts/{id}/bookmark", postId), minsu)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookmarked").value(true));
        perform(put("/api/posts/{id}/bookmark", postId), minsu).andExpect(status().isOk());
        perform(get("/api/users/me/bookmarks"), minsu).andExpect(jsonPath("$.totalElements").value(1));

        perform(delete("/api/posts/{id}/bookmark", postId), minsu)
                .andExpect(jsonPath("$.bookmarked").value(false));
        perform(get("/api/users/me/bookmarks"), minsu).andExpect(jsonPath("$.content", empty()));
    }

    @Test
    @DisplayName("내 북마크 목록: 북마크한 최신순, 남의 북마크는 안 나온다")
    void myBookmarks() throws Exception {
        Long second = insertPost(nayoung.blogId(), defaultCategoryId(nayoung.blogId()), "PUBLISHED", "PUBLIC");
        perform(put("/api/posts/{id}/bookmark", postId), minsu);
        perform(put("/api/posts/{id}/bookmark", second), minsu);
        perform(put("/api/posts/{id}/bookmark", postId), nayoung);   // 나영의 북마크

        perform(get("/api/users/me/bookmarks"), minsu)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(second.intValue(), postId.intValue())))
                .andExpect(jsonPath("$.content[0].author.username").value("nayoung"))
                .andExpect(jsonPath("$.content[0].bookmarkedAt").value(matchesPattern(".*\\+09:00$")))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("북마크한 글이 비공개가 되거나 카테고리가 비공개가 되면 빠지고, 다시 공개되면 돌아온다 (K-02)")
    void hiddenPostsLeaveBookmarkList() throws Exception {
        perform(put("/api/posts/{id}/bookmark", postId), minsu);

        jdbcTemplate.update("UPDATE posts SET visibility = 'PRIVATE' WHERE id = ?", postId);
        perform(get("/api/users/me/bookmarks"), minsu).andExpect(jsonPath("$.content", empty()));

        jdbcTemplate.update("UPDATE posts SET visibility = 'PUBLIC' WHERE id = ?", postId);
        jdbcTemplate.update("UPDATE categories SET visibility = 'PRIVATE' WHERE blog_id = ?", nayoung.blogId());
        perform(get("/api/users/me/bookmarks"), minsu).andExpect(jsonPath("$.content", empty()));

        jdbcTemplate.update("UPDATE categories SET visibility = 'PUBLIC' WHERE blog_id = ?", nayoung.blogId());
        perform(get("/api/users/me/bookmarks"), minsu).andExpect(jsonPath("$.totalElements").value(1));
    }
}
