package com.ownlog.global.storage;

import com.ownlog.global.util.MarkdownText;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 고아 파일 정리 배치 (이미지 흐름 문서 6장 B). 매일 새벽 4시(한국 시간)에 한 번 돌아요.
 *
 * 고아 파일은 "업로드했지만 어디에서도 쓰지 않는 파일"이에요. 이미지를 고르는 즉시 업로드되기 때문에,
 * 글을 저장하지 않고 나가거나 저장이 실패하면 생겨요 (업로드와 저장이 다른 요청이라 한 트랜잭션으로 묶을 수 없어요).
 *
 * 안전장치 두 가지
 * 1. 유예 시간(grace, 기본 24시간): 그보다 최근에 바뀐 파일은 건드리지 않아요. 지금 글을 쓰는 중인 사람의 이미지를 지우는
 *    Race Condition을 막아요.
 * 2. DB 잠금(pg_try_advisory_xact_lock): 블루-그린 배포 중에는 앱이 두 개 떠 있어서 둘 다 4시에 배치를 시작해요.
 *    먼저 잠금을 잡은 쪽만 돌고, 나머지는 바로 끝나요. 잠금은 트랜잭션이 끝나면 저절로 풀려요.
 */
@Slf4j
@Component
public class OrphanImageCleanupJob {

    /** 이 배치 전용 잠금 번호 (다른 기능이 같은 번호를 쓰지 않게 고정값) */
    static final long LOCK_KEY = 836_2026_1016L;

    private final Path root;
    private final ImageCleanupProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final ImageReferenceChecker imageReferenceChecker;
    private final TransactionTemplate transactionTemplate;

    public OrphanImageCleanupJob(UploadProperties uploadProperties, ImageCleanupProperties properties,
                                 JdbcTemplate jdbcTemplate, ImageReferenceChecker imageReferenceChecker,
                                 TransactionTemplate transactionTemplate) {
        this.root = Path.of(uploadProperties.dir()).toAbsolutePath().normalize();
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.imageReferenceChecker = imageReferenceChecker;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void scheduled() {
        if (properties.enabled()) {
            run(Instant.now());
        }
    }

    /**
     * 정리를 한 번 실행하고 지운 파일 수를 돌려줘요 (테스트에서도 직접 불러요).
     * pg_try_advisory_xact_lock은 트랜잭션이 끝날 때 풀리므로 정리가 끝날 때까지 트랜잭션을 붙잡고 있어야 해요.
     * 예전에는 @Transactional을 썼는데, scheduled()가 같은 객체의 this.run()을 부르면 Spring 프록시를 거치지 않아
     * 어노테이션이 조용히 무시돼요 (self-invocation, Sonar S2229). 그래서 어떤 경로로 불려도 똑같이 동작하게
     * TransactionTemplate로 트랜잭션을 직접 열어요. DB를 고치지 않아서 롤백할 것은 없어요.
     */
    public int run(Instant now) {
        Integer deleted = transactionTemplate.execute(status -> runInTransaction(now));
        return deleted == null ? 0 : deleted;
    }

    private int runInTransaction(Instant now) {
        Boolean locked = jdbcTemplate.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, LOCK_KEY);
        if (!Boolean.TRUE.equals(locked)) {
            log.info("이미지 정리: 다른 서버가 이미 정리 중이라 건너뛰어요");
            return 0;
        }
        if (!Files.isDirectory(root)) {
            return 0;
        }
        Instant cutoff = now.minus(properties.grace());
        Set<String> referenced = loadReferencedUrls();   // 한 번에 모아서, 파일마다 DB를 훑지 않게 해요
        int deleted = 0;
        for (Path file : oldFiles(cutoff)) {
            String url = LocalStorageService.URL_PREFIX + root.relativize(file).toString().replace('\\', '/');
            // 모은 뒤에 새로 저장된 글이 이 파일을 쓸 수도 있어서, 지우기 직전에 한 번 더 확인해요
            if (referenced.contains(url) || imageReferenceChecker.isUsedAnywhere(url)) {
                continue;
            }
            try {
                if (Files.deleteIfExists(file)) {
                    deleted++;
                }
            } catch (IOException e) {
                log.warn("이미지 정리: 파일 삭제 실패 {}", url, e);
            }
        }
        log.info("이미지 정리: 고아 파일 {}개 삭제 ({} 이전 파일 대상)", deleted, cutoff);
        return deleted;
    }

    /** 프로필 사진, 글 썸네일, 글 본문(임시저장 포함)에서 쓰는 우리 업로드 주소 전부 */
    private Set<String> loadReferencedUrls() {
        Set<String> urls = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT profile_image_url FROM users WHERE profile_image_url LIKE '/uploads/%'", String.class));
        jdbcTemplate.query("SELECT thumbnail_url, content FROM posts", rs -> {
            urls.addAll(UnusedImageCleaner.imagesOf(rs.getString("thumbnail_url"), rs.getString("content")));
        });
        return urls;
    }

    /** 업로드 폴더의 파일 중 cutoff보다 오래전에 바뀐 것 */
    private List<Path> oldFiles(Instant cutoff) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> isOlderThan(file, cutoff))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isOlderThan(Path file, Instant cutoff) {
        try {
            return Files.getLastModifiedTime(file).toInstant().isBefore(cutoff);
        } catch (IOException e) {
            return false;   // 읽을 수 없으면 지우지 않아요 (안전한 쪽)
        }
    }
}
