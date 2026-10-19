package com.ownlog.global.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 고아 파일 정리 배치 설정 (application.yaml의 ownlog.upload.cleanup.*).
 *
 * @param enabled 끄면 배치가 아무것도 하지 않아요
 * @param grace   이 시간보다 오래된 파일만 지워요. 방금 올리고 아직 글을 저장하지 않은 파일을 지우지 않으려는 유예 시간이에요
 */
@ConfigurationProperties(prefix = "ownlog.upload.cleanup")
public record ImageCleanupProperties(@DefaultValue("true") boolean enabled, @DefaultValue("24h") Duration grace) {
}
