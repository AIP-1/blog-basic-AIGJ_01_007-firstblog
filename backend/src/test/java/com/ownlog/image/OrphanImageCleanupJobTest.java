package com.ownlog.image;

import static org.assertj.core.api.Assertions.assertThat;

import com.ownlog.IntegrationTest;
import com.ownlog.global.storage.OrphanImageCleanupJob;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** 고아 파일 정리 배치 (docs/image-flow.md 6장 B): 유예 시간, 참조 확인, 동시 실행 잠금 */
class OrphanImageCleanupJobTest extends IntegrationTest {

    private static final Path UPLOAD_DIR = Path.of("build/test-uploads");

    @Autowired OrphanImageCleanupJob job;
    @Autowired TransactionTemplate transactionTemplate;

    /** 업로드 폴더에 파일을 만들고, 수정 시각을 age만큼 과거로 돌려요. 돌려주는 값은 그 파일의 주소예요 */
    private String fileAged(Duration age) throws Exception {
        String relative = "2026/10/" + UUID.randomUUID() + ".png";
        Path file = UPLOAD_DIR.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {1, 2, 3});
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(age)));
        return "/uploads/" + relative;
    }

    private static Path fileOf(String url) {
        return UPLOAD_DIR.resolve(url.substring("/uploads/".length()));
    }

    @Test
    @DisplayName("1. 24시간 넘은 고아 파일은 지우고, 방금 올린 고아 파일(글을 쓰는 중일 수 있음)은 남긴다")
    void deletesOnlyOldOrphans() throws Exception {
        String oldOrphan = fileAged(Duration.ofHours(25));
        String freshOrphan = fileAged(Duration.ofMinutes(10));

        job.run(Instant.now());

        assertThat(fileOf(oldOrphan)).doesNotExist();
        assertThat(fileOf(freshOrphan)).exists();
    }

    @Test
    @DisplayName("2. 오래됐어도 글 본문·썸네일·임시저장·프로필에서 쓰면 남긴다")
    void keepsReferencedFiles() throws Exception {
        TestUser nayoung = signupAndLogin("nayoung");
        String inContent = fileAged(Duration.ofDays(3));
        String asThumbnail = fileAged(Duration.ofDays(3));
        String inDraft = fileAged(Duration.ofDays(3));
        String asProfile = fileAged(Duration.ofDays(3));
        Long categoryId = defaultCategoryId(nayoung.blogId());
        Long postId = insertPost(nayoung.blogId(), categoryId, "PUBLISHED", "PUBLIC");
        jdbcTemplate.update("UPDATE posts SET content = ?, thumbnail_url = ? WHERE id = ?",
                "본문 ![a](" + inContent + ")", asThumbnail, postId);
        Long draftId = insertPost(nayoung.blogId(), categoryId, "DRAFT", "PUBLIC");
        jdbcTemplate.update("UPDATE posts SET content = ? WHERE id = ?", "<img src=\"" + inDraft + "\">", draftId);
        jdbcTemplate.update("UPDATE users SET profile_image_url = ? WHERE id = ?", asProfile, nayoung.userId());

        int deleted = job.run(Instant.now());

        assertThat(deleted).isZero();
        assertThat(fileOf(inContent)).exists();
        assertThat(fileOf(asThumbnail)).exists();
        assertThat(fileOf(inDraft)).exists();
        assertThat(fileOf(asProfile)).exists();
    }

    @Test
    @DisplayName("3. 다른 서버(블루-그린의 다른 색)가 정리 중이면 건너뛴다 (DB 잠금)")
    void skipsWhenAnotherServerHoldsLock() throws Exception {
        String oldOrphan = fileAged(Duration.ofDays(2));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread otherServer = new Thread(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.queryForObject("SELECT pg_advisory_xact_lock(?)", Object.class, 836_2026_1016L);
            locked.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);   // 정리 중인 척 잠금을 붙잡고 있어요
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        otherServer.start();
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        int deleted = job.run(Instant.now());

        release.countDown();
        otherServer.join();
        assertThat(deleted).isZero();
        assertThat(fileOf(oldOrphan)).exists();   // 잠금이 풀린 다음 배치가 지울 거예요
    }
}
