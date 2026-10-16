package com.ownlog.global.config;

import com.ownlog.global.storage.ImageCleanupProperties;
import com.ownlog.global.storage.UploadProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 업로드 설정. @EnableScheduling: 고아 파일 정리 배치(OrphanImageCleanupJob)를 정해진 시각에 돌려요 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({UploadProperties.class, ImageCleanupProperties.class})
public class StorageConfig {
}
