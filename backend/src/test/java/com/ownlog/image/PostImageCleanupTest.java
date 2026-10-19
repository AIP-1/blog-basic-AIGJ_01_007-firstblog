package com.ownlog.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ownlog.IntegrationTest;
import com.ownlog.global.security.LoginUser;
import com.ownlog.post.service.PostService;
import com.ownlog.user.domain.UserRole;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/** 글을 지우거나 고치면 더는 쓰지 않는 업로드 파일을 커밋 후에 지워요 (docs/image-flow.md 6장 A) */
class PostImageCleanupTest extends IntegrationTest {

    private static final Path UPLOAD_DIR = Path.of("build/test-uploads");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

    @Autowired MockMvc mockMvc;
    @Autowired PostService postService;
    @Autowired TransactionTemplate transactionTemplate;

    TestUser nayoung;
    Long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        nayoung = signupAndLogin("nayoung");
        categoryId = defaultCategoryId(nayoung.blogId());
    }

    private String upload(TestUser user) throws Exception {
        String body = mockMvc.perform(multipart("/api/images")
                        .file(new MockMultipartFile("file", "a.png", "image/png", PNG))
                        .header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.url");
    }

    private static Path fileOf(String url) {
        return UPLOAD_DIR.resolve(url.substring("/uploads/".length()));
    }

    private ResultActions call(MockHttpServletRequestBuilder request, TestUser user, String json) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, user.bearer());
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    private String postJson(String content, String thumbnailUrl) {
        return postJson(content, thumbnailUrl, categoryId);
    }

    private String postJson(String content, String thumbnailUrl, Long categoryId) {
        return """
                {"title":"t","content":"%s","categoryId":%d%s}
                """.formatted(content.replace("\"", "\\\""), categoryId,
                thumbnailUrl == null ? "" : ",\"thumbnailUrl\":\"" + thumbnailUrl + "\"");
    }

    private Long createPost(TestUser user, String content, String thumbnailUrl) throws Exception {
        // 글은 쓰는 사람 블로그의 기본 카테고리에 넣어요
        String body = call(post("/api/posts"), user, postJson(content, thumbnailUrl, defaultCategoryId(user.blogId())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    @DisplayName("1. 글을 지우면 본문 이미지(![], <img>)와 썸네일 파일이 모두 지워진다")
    void deletePostDeletesItsImages() throws Exception {
        String inBody = upload(nayoung);
        String inTag = upload(nayoung);
        String thumbnail = upload(nayoung);
        Long postId = createPost(nayoung, "사진 ![a](" + inBody + ") 그리고 <img src=\"" + inTag + "\">", thumbnail);

        call(delete("/api/posts/" + postId), nayoung, null).andExpect(status().isNoContent());

        assertThat(fileOf(inBody)).doesNotExist();
        assertThat(fileOf(inTag)).doesNotExist();
        assertThat(fileOf(thumbnail)).doesNotExist();
    }

    @Test
    @DisplayName("2. 다른 글이 같은 이미지를 쓰고 있으면 지우지 않는다")
    void keepImageUsedByAnotherPost() throws Exception {
        String shared = upload(nayoung);
        Long first = createPost(nayoung, "![a](" + shared + ")", null);
        createPost(signupAndLogin("minsu"), "남의 글에 같은 주소 ![b](" + shared + ")", null);

        call(delete("/api/posts/" + first), nayoung, null).andExpect(status().isNoContent());

        assertThat(fileOf(shared)).exists();
    }

    @Test
    @DisplayName("3. 내 프로필 사진으로도 쓰는 이미지는 글을 지워도 남는다 (isUsedElsewhere 함정)")
    void keepImageUsedAsMyProfile() throws Exception {
        String photo = upload(nayoung);
        call(put("/api/users/me"), nayoung, """
                {"nickname":"나영","bio":null,"profileImageUrl":"%s"}
                """.formatted(photo)).andExpect(status().isOk());
        Long postId = createPost(nayoung, "![me](" + photo + ")", null);

        call(delete("/api/posts/" + postId), nayoung, null).andExpect(status().isNoContent());

        assertThat(fileOf(photo)).exists();
    }

    @Test
    @DisplayName("4. 글을 고치면서 뺀 이미지만 지우고, 그대로 둔 이미지는 남긴다")
    void updateDeletesOnlyRemovedImages() throws Exception {
        String keep = upload(nayoung);
        String removed = upload(nayoung);
        Long postId = createPost(nayoung, "![k](" + keep + ") ![r](" + removed + ")", null);

        call(put("/api/posts/" + postId), nayoung, postJson("![k](" + keep + ")", null))
                .andExpect(status().isOk());

        assertThat(fileOf(keep)).exists();
        assertThat(fileOf(removed)).doesNotExist();
    }

    @Test
    @DisplayName("5. 임시저장 글을 지우면 그 이미지도 지워진다")
    void deleteDraftDeletesImages() throws Exception {
        String image = upload(nayoung);
        String body = call(post("/api/drafts"), nayoung, """
                {"content":"![d](%s)"}
                """.formatted(image)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long draftId = ((Number) JsonPath.read(body, "$.id")).longValue();

        call(delete("/api/drafts/" + draftId), nayoung, null).andExpect(status().isNoContent());

        assertThat(fileOf(image)).doesNotExist();
    }

    @Test
    @DisplayName("6. 탈퇴하면 내 글이 쓰던 이미지도 지워진다")
    void withdrawDeletesPostImages() throws Exception {
        String image = upload(nayoung);
        createPost(nayoung, "![w](" + image + ")", null);

        call(delete("/api/users/me"), nayoung, "{\"password\":\"ownlog1234\"}").andExpect(status().isNoContent());

        assertThat(fileOf(image)).doesNotExist();
    }

    @Test
    @DisplayName("7. 글 삭제 트랜잭션이 롤백되면 파일은 그대로 (커밋 뒤에만 지우니까 깨진 이미지가 생기지 않는다)")
    void rollbackKeepsFiles() throws Exception {
        String image = upload(nayoung);
        Long postId = createPost(nayoung, "![x](" + image + ")", null);
        LoginUser me = new LoginUser(nayoung.userId(), "nayoung", UserRole.USER);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            postService.delete(postId, me);                  // 여기서 삭제 이벤트가 예약되지만
            throw new IllegalStateException("일부러 실패");    // 커밋 전에 실패하면
        })).isInstanceOf(IllegalStateException.class);

        assertThat(fileOf(image)).exists();                  // 파일도, 글도 그대로예요
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM posts WHERE id = ?", Integer.class, postId))
                .isEqualTo(1);
    }
}
