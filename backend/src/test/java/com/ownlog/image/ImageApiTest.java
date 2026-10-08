package com.ownlog.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ownlog.IntegrationTest;
import com.ownlog.image.service.ImageService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** API 11.1 이미지 업로드 (P-03) */
class ImageApiTest extends IntegrationTest {

    private static final Path UPLOAD_DIR = Path.of("build/test-uploads");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1};

    @Autowired MockMvc mockMvc;

    TestUser nayoung;

    @BeforeEach
    void setUp() throws Exception {
        nayoung = signupAndLogin("nayoung");
    }

    private ResultActions upload(String filename, byte[] content, TestUser user) throws Exception {
        var request = multipart("/api/images").file(new MockMultipartFile("file", filename, "image/png", content));
        if (user != null) {
            request.header(HttpHeaders.AUTHORIZATION, user.bearer());
        }
        return mockMvc.perform(request);
    }

    private String uploadAndGetUrl(String filename, byte[] content) throws Exception {
        String body = upload(filename, content, nayoung).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.url");
    }

    private Path fileOf(String url) {
        return UPLOAD_DIR.resolve(url.substring("/uploads/".length()));
    }

    @Test
    @DisplayName("진짜 PNG는 201, UUID 이름으로 저장되고 그 주소로 누구나 볼 수 있다")
    void uploadPng() throws Exception {
        String url = uploadAndGetUrl("내 사진 (1).png", PNG);

        assertThat(url).matches("/uploads/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.png");   // 사용자가 올린 이름은 안 써요
        assertThat(fileOf(url)).exists();
        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PNG));
    }

    @Test
    @DisplayName("이름은 .png지만 내용이 JPEG면 받아 주고 .jpg로 저장한다")
    void extensionFollowsContent() throws Exception {
        upload("photo.png", JPEG, nayoung)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value(matchesPattern(".*\\.jpg$")));
    }

    @Test
    @DisplayName("이미지로 위장한 텍스트, SVG, 실행 파일은 400 INVALID_FILE_TYPE")
    void rejectsNonImages() throws Exception {
        byte[] script = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);

        upload("cat.png", script, nayoung)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));
        upload("logo.svg", "<svg onload=alert(1)/>".getBytes(StandardCharsets.UTF_8), nayoung)
                .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));
        upload("virus.exe", PNG, nayoung)
                .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));
        upload("empty.png", new byte[0], nayoung)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("5MB를 넘으면 400 FILE_TOO_LARGE")
    void tooLarge() throws Exception {
        byte[] big = Arrays.copyOf(PNG, (int) ImageService.MAX_SIZE + 1);

        upload("big.png", big, nayoung)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    @DisplayName("file 파트가 없으면 400, 방문자는 401")
    void missingPartAndVisitor() throws Exception {
        mockMvc.perform(multipart("/api/images").header(HttpHeaders.AUTHORIZATION, nayoung.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        upload("a.png", PNG, null).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("업로드 → 프로필 사진으로 설정 → 다른 사진으로 바꾸면 예전 파일이 지워진다 (4.5와 연결)")
    void uploadThenReplaceProfileImage() throws Exception {
        String first = uploadAndGetUrl("first.png", PNG);
        String second = uploadAndGetUrl("second.png", PNG);

        for (String url : new String[]{first, second}) {
            mockMvc.perform(put("/api/users/me").header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nickname\":\"나영\",\"profileImageUrl\":\"%s\"}".formatted(url)))
                    .andExpect(status().isOk());
        }

        assertThat(fileOf(first)).doesNotExist();
        assertThat(fileOf(second)).exists();
    }
}
