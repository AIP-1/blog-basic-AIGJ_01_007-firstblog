package com.ownlog.auth.oauth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.ownlog.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 키 이름: 짧은 이름(GOOGLE_CLIENT_ID)과 예전 이름(OAUTH_KAKAO_CLIENT_ID)을 둘 다 읽어요 */
@TestPropertySource(properties = {
        "GOOGLE_CLIENT_ID=google-test-id", "GOOGLE_CLIENT_SECRET=google-test-secret",
        "OAUTH_KAKAO_CLIENT_ID=kakao-test-id", "OAUTH_KAKAO_CLIENT_SECRET=kakao-test-secret",
})
class OAuthEnvNameTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("GOOGLE_CLIENT_ID 같은 짧은 이름도, OAUTH_ 예전 이름도 켜진다")
    void bothNamesWork() throws Exception {
        mockMvc.perform(get("/api/auth/oauth/providers"))
                .andExpect(jsonPath("$.providers.length()").value(2))
                .andExpect(jsonPath("$.providers[0]").value("kakao"))
                .andExpect(jsonPath("$.providers[1]").value("google"));
    }
}
