package com.ownlog.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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

/** API 5.3 ~ 5.7 카테고리 */
class CategoryApiTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;

    TestUser nayoung;
    TestUser minsu;

    @BeforeEach
    void setUp() throws Exception {
        nayoung = signupAndLogin("nayoung");
        minsu = signupAndLogin("minsu");
    }

    private ResultActions create(TestUser requester, String name, String visibility) throws Exception {
        return mockMvc.perform(post("/api/blogs/{blogId}/categories", requester.blogId())
                .header(HttpHeaders.AUTHORIZATION, requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","visibility":"%s"}
                        """.formatted(name, visibility)));
    }

    private Long createAndGetId(TestUser requester, String name, String visibility) throws Exception {
        String body = create(requester, name, visibility).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions sidebar(TestUser viewer) throws Exception {
        var request = get("/api/blogs/nayoung/categories");
        if (viewer != null) {
            request.header(HttpHeaders.AUTHORIZATION, viewer.bearer());
        }
        return mockMvc.perform(request);
    }

    private ResultActions update(TestUser requester, Long categoryId, String name, String visibility) throws Exception {
        return mockMvc.perform(put("/api/categories/{id}", categoryId)
                .header(HttpHeaders.AUTHORIZATION, requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","visibility":"%s"}
                        """.formatted(name, visibility)));
    }

    private ResultActions deleteCategory(TestUser requester, Long categoryId) throws Exception {
        return mockMvc.perform(delete("/api/categories/{id}", categoryId)
                .header(HttpHeaders.AUTHORIZATION, requester.bearer()));
    }

    @Test
    @DisplayName("생성하면 201, 맨 아래 순서(1), 글 수 0")
    void createCategory() throws Exception {
        create(nayoung, "개발", "PUBLIC")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("개발"))
                .andExpect(jsonPath("$.displayOrder").value(1))
                .andExpect(jsonPath("$.isDefault").value(false))
                .andExpect(jsonPath("$.postCount").value(0));
    }

    @Test
    @DisplayName("같은 이름은 409 DUPLICATE_CATEGORY_NAME")
    void duplicateName() throws Exception {
        create(nayoung, "개발", "PUBLIC").andExpect(status().isCreated());
        create(nayoung, "개발", "PUBLIC")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CATEGORY_NAME"));
    }

    @Test
    @DisplayName("\"미분류\" 포함 30개가 되면 더 만들 수 없다 (400 CATEGORY_LIMIT_EXCEEDED)")
    void categoryLimit() throws Exception {
        for (int i = 1; i <= 29; i++) {
            create(nayoung, "카테고리" + i, "PUBLIC").andExpect(status().isCreated());
        }
        create(nayoung, "31번째", "PUBLIC")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATEGORY_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("순서 변경 후 사이드바가 그 순서로 나온다")
    void reorder() throws Exception {
        Long defaultId = defaultCategoryId(nayoung.blogId());
        Long devId = createAndGetId(nayoung, "개발", "PUBLIC");
        Long travelId = createAndGetId(nayoung, "여행", "PUBLIC");

        mockMvc.perform(put("/api/blogs/{blogId}/categories/order", nayoung.blogId())
                        .header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryIds\":[%d,%d,%d]}".formatted(travelId, defaultId, devId)))
                .andExpect(status().isNoContent());

        sidebar(nayoung).andExpect(jsonPath("$.categories[*].name", contains("여행", "미분류", "개발")));
    }

    @Test
    @DisplayName("순서 변경에서 카테고리를 빠뜨리면 400 INVALID_CATEGORY_ORDER")
    void reorderMissingCategory() throws Exception {
        Long defaultId = defaultCategoryId(nayoung.blogId());
        createAndGetId(nayoung, "개발", "PUBLIC");

        mockMvc.perform(put("/api/blogs/{blogId}/categories/order", nayoung.blogId())
                        .header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryIds\":[%d]}".formatted(defaultId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATEGORY_ORDER"));
    }

    @Test
    @DisplayName("사이드바: 방문자에게는 비공개 카테고리를 숨기고 볼 수 있는 글만 센다")
    void sidebarCountsForVisitorAndOwner() throws Exception {
        Long devId = createAndGetId(nayoung, "개발", "PUBLIC");
        Long diaryId = createAndGetId(nayoung, "일기", "PRIVATE");
        insertPost(nayoung.blogId(), devId, "PUBLISHED", "PUBLIC");     // 모두에게 보임
        insertPost(nayoung.blogId(), devId, "PUBLISHED", "PRIVATE");    // 주인에게만
        insertPost(nayoung.blogId(), devId, "DRAFT", "PUBLIC");         // 아무도 세지 않음
        insertPost(nayoung.blogId(), diaryId, "PUBLISHED", "PUBLIC");   // 비공개 카테고리 → 주인에게만

        sidebar(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPostCount").value(1))
                .andExpect(jsonPath("$.categories[*].name", contains("미분류", "개발")))
                .andExpect(jsonPath("$.categories[1].postCount").value(1));

        sidebar(nayoung)
                .andExpect(jsonPath("$.totalPostCount").value(3))
                .andExpect(jsonPath("$.categories[*].name", contains("미분류", "개발", "일기")))
                .andExpect(jsonPath("$.categories[1].postCount").value(2))
                .andExpect(jsonPath("$.categories[2].postCount").value(1))
                .andExpect(jsonPath("$.categories[2].visibility").value("PRIVATE"));
    }

    @Test
    @DisplayName("기본 카테고리는 삭제할 수 없다 (400 DEFAULT_CATEGORY_NOT_DELETABLE)")
    void cannotDeleteDefault() throws Exception {
        deleteCategory(nayoung, defaultCategoryId(nayoung.blogId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEFAULT_CATEGORY_NOT_DELETABLE"));
    }

    @Test
    @DisplayName("공개 카테고리를 삭제하면 글(임시저장 포함)이 \"미분류\"로 옮겨진다")
    void deletePublicCategoryMovesPosts() throws Exception {
        Long defaultId = defaultCategoryId(nayoung.blogId());
        Long devId = createAndGetId(nayoung, "개발", "PUBLIC");
        Long publishedId = insertPost(nayoung.blogId(), devId, "PUBLISHED", "PUBLIC");
        Long draftId = insertPost(nayoung.blogId(), devId, "DRAFT", "PUBLIC");

        deleteCategory(nayoung, devId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movedPostCount").value(2))
                .andExpect(jsonPath("$.movedToCategoryId").value(defaultId));

        assertThat(postRow(publishedId)).containsEntry("category_id", defaultId).containsEntry("visibility", "PUBLIC");
        assertThat(postRow(draftId)).containsEntry("category_id", defaultId);
    }

    @Test
    @DisplayName("비공개 카테고리를 삭제하면 옮겨진 글이 비공개로 바뀐다 (숨긴 글 노출 방지, B-05)")
    void deletePrivateCategoryHidesPosts() throws Exception {
        Long defaultId = defaultCategoryId(nayoung.blogId());
        Long diaryId = createAndGetId(nayoung, "일기", "PRIVATE");
        Long postId = insertPost(nayoung.blogId(), diaryId, "PUBLISHED", "PUBLIC");

        deleteCategory(nayoung, diaryId).andExpect(status().isOk());

        assertThat(postRow(postId)).containsEntry("category_id", defaultId).containsEntry("visibility", "PRIVATE");
        sidebar(null).andExpect(jsonPath("$.totalPostCount").value(0));   // 방문자에게 새어 나가지 않아요
    }

    @Test
    @DisplayName("다른 회원의 공개 카테고리 수정은 403, 비공개 카테고리 수정은 404 (존재를 숨김)")
    void otherUserUpdate() throws Exception {
        Long devId = createAndGetId(nayoung, "개발", "PUBLIC");
        Long diaryId = createAndGetId(nayoung, "일기", "PRIVATE");

        update(minsu, devId, "가로채기", "PUBLIC")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        update(minsu, diaryId, "가로채기", "PUBLIC")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    @DisplayName("주인은 이름과 공개 범위를 바꿀 수 있다 (\"미분류\" 이름 변경도 가능)")
    void ownerUpdate() throws Exception {
        update(nayoung, defaultCategoryId(nayoung.blogId()), "기타", "PRIVATE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("기타"))
                .andExpect(jsonPath("$.isDefault").value(true))
                .andExpect(jsonPath("$.visibility").value("PRIVATE"));
    }

    private Map<String, Object> postRow(Long postId) {
        return jdbcTemplate.queryForMap("SELECT category_id, visibility FROM posts WHERE id = ?", postId);
    }
}
