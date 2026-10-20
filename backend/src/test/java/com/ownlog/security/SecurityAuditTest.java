package com.ownlog.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ownlog.IntegrationTest;
import java.time.Duration;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** 전체 점검(2026-10-12)에서 찾은 보안 문제의 회귀 테스트 */
class SecurityAuditTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;

    TestUser nayoung;
    TestUser minsu;

    @BeforeEach
    void setUp() throws Exception {
        nayoung = signupAndLogin("nayoung");
        minsu = signupAndLogin("minsu");
    }

    @Test
    @DisplayName("인코딩한 주소(/%61pi/...)로 로그인 검사를 건너뛸 수 없다")
    void encodedPathDoesNotBypassAuthentication() throws Exception {
        mockMvc.perform(get(URI.create("/%61pi/users/me"))).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart(URI.create("/%61pi/images"))
                        .file(new MockMultipartFile("file", "a.png", "image/png", new byte[] {1, 2, 3})))
                .andExpect(status().isUnauthorized());
        // 화면 주소는 그대로 누구나
        mockMvc.perform(get("/@nayoung")).andExpect(result ->
                assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
    }

    @Test
    @DisplayName("남의 이미지 주소를 프로필로 넣었다가 바꿔도 그 파일은 지워지지 않는다")
    void cannotDeleteOthersImageThroughProfile() throws Exception {
        Path victimFile = Path.of("build/test-uploads/audit/minsu-post.png");
        Files.createDirectories(victimFile.getParent());
        Files.writeString(victimFile, "image");
        String url = "/uploads/audit/minsu-post.png";
        mockMvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, minsu.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"민수 글\",\"content\":\"![](%s)\"}".formatted(url)))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/users/me").header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"나영\",\"profileImageUrl\":\"%s\"}".formatted(url)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/users/me").header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"나영\",\"profileImageUrl\":null}"))
                .andExpect(status().isOk());

        // 커밋 후 리스너가 돌 시간을 준 뒤에도 남의 파일이 그대로인지: 300ms 동안 계속 존재해야 해요 (S2925: sleep 대신 Awaitility)
        await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(victimFile).exists());
    }

    @Test
    @DisplayName("아무도 쓰지 않는 내 프로필 사진은 바꾸면 지워진다 (기존 동작 유지)")
    void unusedOwnImageIsStillDeleted() throws Exception {
        Path file = Path.of("build/test-uploads/audit/nayoung-profile.png");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "image");
        mockMvc.perform(put("/api/users/me").header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"나영\",\"profileImageUrl\":\"/uploads/audit/nayoung-profile.png\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/users/me").header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"나영\",\"profileImageUrl\":null}"))
                .andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(file).doesNotExist());
    }

    @Test
    @DisplayName("72바이트가 넘는 비밀번호(한글 포함)는 500이 아니라 400 입력 오류")
    void tooLongUtf8PasswordIs400() throws Exception {
        String longPassword = "a1" + "가".repeat(30);   // 32자, 92바이트
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"kor@test.com","password":"%s","passwordConfirm":"%s","username":"korean","nickname":"케이"}
                        """.formatted(longPassword, longPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
        mockMvc.perform(patch("/api/users/me/password").header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"currentPassword":"ownlog1234","newPassword":"%s","newPasswordConfirm":"%s"}
                        """.formatted(longPassword, longPassword)))
                .andExpect(status().isBadRequest());
        // 72바이트 이하의 한글 비밀번호는 그대로 돼요
        String shortKorean = "a1가나다라마바";
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"kor2@test.com","password":"%s","passwordConfirm":"%s","username":"korean2","nickname":"케이"}
                        """.formatted(shortKorean, shortKorean)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("비공개·임시저장 글에만 있는 태그는 저장된 표기를 드러내지 않는다")
    void tagNameDoesNotLeak() throws Exception {
        mockMvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, minsu.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\",\"tags\":[\"ProjectX\"],\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/tags/PROJECTX/posts"))
                .andExpect(jsonPath("$.tag").value("PROJECTX"))
                .andExpect(jsonPath("$.posts.totalElements").value(0));
    }

    @Test
    @DisplayName("받는 사람에게 보낸 사람의 비공개 카테고리 이름을 숨기고, 열린 편지를 고치려 하면 403")
    void recipientViewHidesPrivateCategoryAndGets403() throws Exception {
        Long privateCategory = jdbcTemplate.queryForObject("""
                INSERT INTO categories (blog_id, name, display_order, visibility, created_at, updated_at)
                VALUES (?, '퇴사 준비', 1, 'PRIVATE', now(), now()) RETURNING id
                """, Long.class, nayoung.blogId());
        String openAt = OffsetDateTime.now(ZoneOffset.ofHours(9)).plusHours(1).toString();
        String body = mockMvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"편지","content":"c","categoryId":%d,"openAt":"%s","recipientUsername":"minsu"}
                        """.formatted(privateCategory, openAt)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        mockMvc.perform(get("/api/blogs/nayoung/posts/" + id).header(HttpHeaders.AUTHORIZATION, minsu.bearer()))
                .andExpect(jsonPath("$.category.name").value("비공개 카테고리"))
                .andExpect(jsonPath("$.category.id").doesNotExist());
        mockMvc.perform(get("/api/blogs/nayoung/posts/" + id).header(HttpHeaders.AUTHORIZATION, nayoung.bearer()))
                .andExpect(jsonPath("$.category.name").value("퇴사 준비"));

        jdbcTemplate.update("UPDATE posts SET sealed_at = now() - interval '1 day', published_at = now() - interval '1 minute' WHERE id = ?", id);
        mockMvc.perform(get("/api/blogs/nayoung/posts/" + id).header(HttpHeaders.AUTHORIZATION, minsu.bearer()))
                .andExpect(jsonPath("$.category.name").value("비공개 카테고리"));
        mockMvc.perform(put("/api/posts/{id}", id).header(HttpHeaders.AUTHORIZATION, minsu.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("본문 첫 이미지가 http:// 이면 썸네일로 쓰지 않는다")
    void insecureFirstImageIsNotThumbnail() throws Exception {
        mockMvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"![](http://tracker.example/a.png)\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/posts")).andExpect(jsonPath("$.content[0].thumbnailUrl").doesNotExist());
    }

    @Test
    @DisplayName("좋아요를 누른 글이 비공개가 돼도 좋아요를 취소할 수 있다")
    void canUnlikeAfterPostBecamePrivate() throws Exception {
        String body = mockMvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        mockMvc.perform(put("/api/posts/{id}/like", id).header(HttpHeaders.AUTHORIZATION, minsu.bearer()))
                .andExpect(jsonPath("$.likeCount").value(1));
        jdbcTemplate.update("UPDATE posts SET visibility = 'PRIVATE' WHERE id = ?", id);

        mockMvc.perform(delete("/api/posts/{id}/like", id)
                        .header(HttpHeaders.AUTHORIZATION, minsu.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(0));
        mockMvc.perform(put("/api/posts/{id}/like", id).header(HttpHeaders.AUTHORIZATION, minsu.bearer()))
                .andExpect(status().isNotFound());   // 새로 누르는 건 여전히 볼 수 있는 글만
    }
}
