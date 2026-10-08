package com.ownlog.reaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.ownlog.IntegrationTest;
import com.ownlog.global.security.LoginUser;
import com.ownlog.reaction.service.ReactionService;
import com.ownlog.user.domain.UserRole;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 📚 동시성 학습용 (ERD 3.7): 좋아요 수는 "행이 실제로 들어갔을 때만" DB가 직접 +1 해요.
 * 그래서 많은 사람이 동시에 눌러도, 한 사람이 연타해도 숫자가 정확해요.
 */
class LikeConcurrencyTest extends IntegrationTest {

    @Autowired ReactionService reactionService;

    Long postId;

    @BeforeEach
    void setUp() throws Exception {
        TestUser nayoung = signupAndLogin("nayoung");
        postId = insertPost(nayoung.blogId(), defaultCategoryId(nayoung.blogId()), "PUBLISHED", "PUBLIC");
    }

    /** 가입 API는 BCrypt 때문에 느려서 회원을 SQL로 바로 넣어요 */
    private LoginUser insertUser(int i) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO users (email, password, username, nickname, created_at, updated_at)
                VALUES (?, 'x', ?, ?, now(), now()) RETURNING id
                """, Long.class, "user" + i + "@test.com", "user" + i, "유저" + i);
        return new LoginUser(id, "user" + i, UserRole.USER);
    }

    private void runConcurrently(List<LoginUser> users) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);   // 모두 제출한 뒤 한꺼번에 출발시켜요
        List<Future<?>> futures = new ArrayList<>();
        for (LoginUser user : users) {
            futures.add(pool.submit(() -> {
                start.await();
                reactionService.like(postId, user);
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();   // 하나라도 예외가 나면 테스트가 실패해요
        }
        pool.shutdown();
    }

    private long likeCount() {
        return jdbcTemplate.queryForObject("SELECT like_count FROM posts WHERE id = ?", Long.class, postId);
    }

    private long likeRows() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_likes WHERE post_id = ?", Long.class, postId);
    }

    @Test
    @DisplayName("서로 다른 회원 100명이 동시에 좋아요를 누르면 좋아요 수는 정확히 100")
    void hundredUsers() throws Exception {
        List<LoginUser> users = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            users.add(insertUser(i));
        }

        runConcurrently(users);

        assertThat(likeRows()).isEqualTo(100);
        assertThat(likeCount()).isEqualTo(100);
    }

    @Test
    @DisplayName("한 회원이 동시에 10번 눌러도(연타) 좋아요 수는 정확히 1")
    void sameUserTenTimes() throws Exception {
        LoginUser user = insertUser(0);

        runConcurrently(java.util.Collections.nCopies(10, user));

        assertThat(likeRows()).isEqualTo(1);
        assertThat(likeCount()).isEqualTo(1);
    }
}
