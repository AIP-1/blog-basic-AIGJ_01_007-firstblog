package com.ownlog.global.storage;

/** "이 파일을 지워 주세요" 이벤트. 실제 삭제는 트랜잭션이 커밋된 뒤에 ImageDeleteListener가 해요 */
public record ImageDeleteEvent(String url) {
}
