package com.ownlog.global.storage;

import com.ownlog.global.util.MarkdownText;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 글을 지우거나 고쳐서 더는 쓰지 않게 된 업로드 파일을 정리해요 (이미지 흐름 문서 6장 A).
 *
 * 순서가 중요해요: ① DB 변경(글 삭제·수정) → ② flush → ③ 아무도 안 쓰는지 확인 → ④ 커밋 뒤에 파일 삭제.
 * - ② JPA의 변경은 flush 전까지 DB에 없어서, JdbcTemplate으로 확인하면 방금 지운 글이 아직 있는 것처럼 보여요.
 * - ④ 파일은 롤백할 수 없어서, 트랜잭션이 확정된 다음에만 지워요 (ImageDeleteListener, AFTER_COMMIT).
 *   롤백되면 이벤트가 버려져서 파일이 그대로 남아요 → 깨진 이미지가 생기지 않아요.
 */
@Component
@RequiredArgsConstructor
public class UnusedImageCleaner {

    private final ImageReferenceChecker imageReferenceChecker;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    /** 글 하나가 쓰는 우리 업로드 주소: 썸네일 + 본문 안의 주소 */
    public static Set<String> imagesOf(String thumbnailUrl, String content) {
        Set<String> urls = new LinkedHashSet<>();
        if (thumbnailUrl != null && thumbnailUrl.startsWith(LocalStorageService.URL_PREFIX)) {
            urls.add(thumbnailUrl);
        }
        urls.addAll(MarkdownText.uploadUrls(content));
        return urls;
    }

    /** DB 변경을 마친 뒤 호출해요. 아무도 쓰지 않는 주소만 커밋 후 삭제를 예약해요 */
    public void deleteIfUnused(Collection<String> urls) {
        if (urls.isEmpty()) {
            return;
        }
        entityManager.flush();   // ② 이 트랜잭션의 JPA 변경을 DB에 먼저 보내요 (커밋은 아직)
        for (String url : urls) {
            if (!imageReferenceChecker.isUsedAnywhere(url)) {
                eventPublisher.publishEvent(new ImageDeleteEvent(url));
            }
        }
    }
}
