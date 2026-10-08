package com.ownlog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ownlog.IntegrationTest;
import com.ownlog.blog.domain.Blog;
import com.ownlog.blog.repository.BlogRepository;
import com.ownlog.category.domain.Category;
import com.ownlog.category.repository.CategoryRepository;
import com.ownlog.user.domain.User;
import com.ownlog.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** API 명세서 4.1 회원가입, 4.3 아이디 확인 */
class SignupApiTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired BlogRepository blogRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private ResultActions signup(String email, String password, String passwordConfirm,
                                 String username, String nickname) throws Exception {
        String body = """
                {"email":"%s","password":"%s","passwordConfirm":"%s","username":"%s","nickname":"%s"}
                """.formatted(email, password, passwordConfirm, username, nickname);
        return mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions signupNayoung() throws Exception {
        return signup("Nayoung@Gmail.com", "ownlog1234", "ownlog1234", "nayoung", "나영");
    }

    @Test
    @DisplayName("정상 가입: 회원, 블로그, 기본 카테고리가 함께 만들어진다")
    void signupSuccess() throws Exception {
        signupNayoung()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("nayoung"));

        User user = userRepository.findByEmail("nayoung@gmail.com").orElseThrow();   // 소문자로 저장됨
        assertThat(user.getPassword()).isNotEqualTo("ownlog1234");                   // 원문 저장 안 함
        assertThat(passwordEncoder.matches("ownlog1234", user.getPassword())).isTrue();
        assertThat(user.getCreatedAt()).isNotNull();                                 // JPA Auditing 동작

        Blog blog = blogRepository.findFirstByUserIdOrderByIdAsc(user.getId()).orElseThrow();
        assertThat(blog.getName()).isEqualTo("나영의 블로그");

        List<Category> categories = categoryRepository.findAllByBlogIdOrderByDisplayOrderAscIdAsc(blog.getId());
        assertThat(categories).singleElement().satisfies(category -> {
            assertThat(category.getName()).isEqualTo("미분류");
            assertThat(category.isDefault()).isTrue();
        });
    }

    @Test
    @DisplayName("대소문자만 다른 이메일로 다시 가입하면 409 DUPLICATE_EMAIL")
    void duplicateEmailIgnoringCase() throws Exception {
        signupNayoung().andExpect(status().isCreated());

        signup("nayoung@gmail.com", "ownlog1234", "ownlog1234", "other", "다른사람")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    @DisplayName("같은 아이디로 가입하면 409 DUPLICATE_USERNAME")
    void duplicateUsername() throws Exception {
        signupNayoung().andExpect(status().isCreated());

        signup("other@gmail.com", "ownlog1234", "ownlog1234", "nayoung", "다른사람")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_USERNAME"));
    }

    @Test
    @DisplayName("형식이 틀리면 400 INVALID_INPUT과 함께 어느 칸이 틀렸는지 fieldErrors로 알려준다")
    void validationFail() throws Exception {
        signup("not-an-email", "short", "short", "nayoung", "나영")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors[*].field", containsInAnyOrder("email", "password")));
    }

    @Test
    @DisplayName("비밀번호 확인이 다르면 400 PASSWORD_CONFIRM_MISMATCH")
    void passwordConfirmMismatch() throws Exception {
        signup("nayoung@gmail.com", "ownlog1234", "ownlog9999", "nayoung", "나영")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_CONFIRM_MISMATCH"));
    }

    @Test
    @DisplayName("예약어 아이디는 400 RESERVED_USERNAME")
    void reservedUsername() throws Exception {
        signup("nayoung@gmail.com", "ownlog1234", "ownlog1234", "admin", "나영")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESERVED_USERNAME"));
    }

    @Test
    @DisplayName("아이디 확인: 사용 가능 / 형식 오류 / 예약어 / 중복")
    void checkUsername() throws Exception {
        signupNayoung().andExpect(status().isCreated());

        mockMvc.perform(get("/api/auth/check-username").param("username", "newuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.reason").value(nullValue()));
        mockMvc.perform(get("/api/auth/check-username").param("username", "AB"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("INVALID_FORMAT"));
        mockMvc.perform(get("/api/auth/check-username").param("username", "admin"))
                .andExpect(jsonPath("$.reason").value("RESERVED"));
        mockMvc.perform(get("/api/auth/check-username").param("username", "nayoung"))
                .andExpect(jsonPath("$.reason").value("DUPLICATE"));
    }
}
