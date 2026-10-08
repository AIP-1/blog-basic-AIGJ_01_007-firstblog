package com.ownlog.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ownlog.IntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** API 4.5 프로필 수정, 4.6 비밀번호 변경 */
class UserApiTest extends IntegrationTest {

    private static final Path UPLOAD_DIR = Path.of("build/test-uploads");

    @Autowired MockMvc mockMvc;

    TestUser nayoung;

    @BeforeEach
    void setUp() throws Exception {
        nayoung = signupAndLogin("nayoung");
    }

    private ResultActions updateProfile(String json) throws Exception {
        return mockMvc.perform(put("/api/users/me").header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions changePassword(String current, String newPassword, String confirm) throws Exception {
        return mockMvc.perform(patch("/api/users/me/password").header(HttpHeaders.AUTHORIZATION, nayoung.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword":"%s","newPassword":"%s","newPasswordConfirm":"%s"}
                        """.formatted(current, newPassword, confirm)));
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nayoung@test.com\",\"password\":\"%s\"}".formatted(password)));
    }

    private Path createUploadedFile(String relativePath) throws Exception {
        Path file = UPLOAD_DIR.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "image");
        return file;
    }

    @Test
    @DisplayName("프로필 수정: 닉네임·소개·사진 반영, 소개를 null로 보내면 지워진다")
    void updateProfileSuccess() throws Exception {
        updateProfile("""
                {"nickname":" 나영 ","bio":"백엔드 개발자 지망생","profileImageUrl":"/uploads/p/a.png"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("나영"))
                .andExpect(jsonPath("$.bio").value("백엔드 개발자 지망생"))
                .andExpect(jsonPath("$.profileImageUrl").value("/uploads/p/a.png"))
                .andExpect(jsonPath("$.blog.id").value(nayoung.blogId()));

        updateProfile("""
                {"nickname":"나영","bio":null,"profileImageUrl":"/uploads/p/a.png"}
                """)
                .andExpect(jsonPath("$.bio").value(nullValue()));
    }

    @Test
    @DisplayName("업로드한 이미지가 아닌 주소는 프로필 사진으로 쓸 수 없다")
    void invalidProfileImage() throws Exception {
        for (String url : new String[]{"javascript:alert(1)", "https://evil.example/a.png"}) {
            updateProfile("{\"nickname\":\"나영\",\"profileImageUrl\":\"%s\"}".formatted(url))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("profileImageUrl"));
        }
    }

    @Test
    @DisplayName("사진을 바꾸면 예전 파일은 커밋 후 디스크에서 지워진다")
    void oldProfileImageIsDeleted() throws Exception {
        Path oldFile = createUploadedFile("profile/old.png");
        Path newFile = createUploadedFile("profile/new.png");
        updateProfile("{\"nickname\":\"나영\",\"profileImageUrl\":\"/uploads/profile/old.png\"}").andExpect(status().isOk());

        updateProfile("{\"nickname\":\"나영\",\"profileImageUrl\":\"/uploads/profile/new.png\"}").andExpect(status().isOk());

        assertThat(oldFile).doesNotExist();
        assertThat(newFile).exists();
    }

    @Test
    @DisplayName("비밀번호를 바꾸면 새 비밀번호로만 로그인된다")
    void changePasswordSuccess() throws Exception {
        changePassword("ownlog1234", "newpass5678", "newpass5678").andExpect(status().isNoContent());

        login("newpass5678").andExpect(status().isOk());
        login("ownlog1234").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 401이 아니라 400 WRONG_PASSWORD")
    void wrongCurrentPassword() throws Exception {
        changePassword("wrong1234", "newpass5678", "newpass5678")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));
    }

    @Test
    @DisplayName("새 비밀번호 확인 불일치, 규칙 위반은 400")
    void newPasswordValidation() throws Exception {
        changePassword("ownlog1234", "newpass5678", "newpass0000")
                .andExpect(jsonPath("$.code").value("PASSWORD_CONFIRM_MISMATCH"));
        changePassword("ownlog1234", "short", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));
    }
}
