package com.ownlog.topic;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ownlog.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** API 6.9 토픽 목록 */
class TopicApiTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("토큰 없이도 토픽 11개를 정해진 순서로 돌려준다")
    void getTopics() throws Exception {
        mockMvc.perform(get("/api/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(11))
                .andExpect(jsonPath("$[*].slug", contains(
                        "it", "daily", "travel", "food", "sports", "game",
                        "movie", "music", "book", "pet", "finance")))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("IT·개발"));
    }
}
