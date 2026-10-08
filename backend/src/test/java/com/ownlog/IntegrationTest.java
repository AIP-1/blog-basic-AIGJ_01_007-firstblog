package com.ownlog;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 통합 테스트 공통 설정: 진짜 PostgreSQL(Testcontainers) + MockMvc + test 프로필.
 * 테스트가 끝날 때마다 데이터를 비워서 테스트끼리 영향을 주지 않게 해요. (topics 초기 데이터는 남겨요)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUpDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE users, blogs, categories, posts, tags, post_tags,
                         comments, post_likes, bookmarks, reports
                RESTART IDENTITY CASCADE
                """);
    }
}
