package com.ownlog.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.ownlog.IntegrationTest;
import com.ownlog.post.service.PostQueryService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 📚 동시성 학습용 (ERD 3.7): 조회수를 DB가 직접 더하면(view_count = view_count + 1)
 * 여러 쓰레드가 동시에 조회해도 하나도 빠지지 않아요.
 */
class ViewCountConcurrencyTest extends IntegrationTest {

    private static final int VISITORS = 50;

    @Autowired PostQueryService postQueryService;

    @Test
    @DisplayName("방문자 50명이 동시에 조회하면 조회수는 정확히 50")
    void concurrentViews() throws Exception {
        TestUser nayoung = signupAndLogin("nayoung");
        Long postId = insertPost(nayoung.blogId(), defaultCategoryId(nayoung.blogId()), "PUBLISHED", "PUBLIC");

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);   // 모두 제출한 뒤 한꺼번에 출발시켜요
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < VISITORS; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                postQueryService.getDetail("nayoung", postId, null);
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();   // 하나라도 예외가 나면 테스트가 실패해요
        }
        pool.shutdown();

        Integer viewCount = jdbcTemplate.queryForObject("SELECT view_count FROM posts WHERE id = ?", Integer.class, postId);
        assertThat(viewCount).isEqualTo(VISITORS);
    }
}
