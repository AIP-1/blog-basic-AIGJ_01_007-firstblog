package com.ownlog.global.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 트랜잭션이 커밋된 뒤에만 파일을 지워요. 롤백되면 아예 실행되지 않아요.
 * 파일은 DB가 아니라서 롤백할 수 없기 때문에, DB 변경이 확정된 다음에 지워야 깨진 이미지가 생기지 않아요.
 */
@Component
@RequiredArgsConstructor
public class ImageDeleteListener {

    private final StorageService storageService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onImageDelete(ImageDeleteEvent event) {
        storageService.delete(event.url());
    }
}
