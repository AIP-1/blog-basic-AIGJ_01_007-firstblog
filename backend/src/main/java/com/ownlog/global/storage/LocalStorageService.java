package com.ownlog.global.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 서버 디스크 저장소. "/uploads/2026/10/x.png" 주소는 {upload.dir}/2026/10/x.png 파일이에요 */
@Slf4j
@Component
public class LocalStorageService implements StorageService {

    public static final String URL_PREFIX = "/uploads/";

    private final Path root;

    public LocalStorageService(UploadProperties properties) {
        this.root = Path.of(properties.dir()).toAbsolutePath().normalize();
    }

    @Override
    public void delete(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            return;   // 외부 주소(https://...)는 우리 파일이 아니에요
        }
        Path file = root.resolve(url.substring(URL_PREFIX.length())).normalize();
        if (!file.startsWith(root)) {   // "/uploads/../../etc/passwd" 같은 경로 조작 차단
            log.warn("업로드 폴더 밖의 파일 삭제 시도: {}", url);
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("파일 삭제 실패: {}", url, e);   // 파일을 못 지워도 사용자 요청은 성공이에요
        }
    }
}
