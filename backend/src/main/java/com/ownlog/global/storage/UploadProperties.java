package com.ownlog.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yaml의 ownlog.upload.* 설정 (dir: 업로드 파일을 저장할 폴더) */
@ConfigurationProperties(prefix = "ownlog.upload")
public record UploadProperties(String dir) {
}
