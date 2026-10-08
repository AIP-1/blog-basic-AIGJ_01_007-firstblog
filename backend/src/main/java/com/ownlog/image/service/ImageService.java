package com.ownlog.image.service;

import com.ownlog.global.error.BusinessException;
import com.ownlog.global.error.ErrorCode;
import com.ownlog.global.storage.ImageType;
import com.ownlog.global.storage.StorageService;
import com.ownlog.image.dto.ImageUploadResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 업로드 (API 11.1, P-03).
 * 클라이언트가 보낸 Content-Type과 파일 이름은 믿지 않고, 파일 앞부분 바이트로 진짜 이미지인지 확인해요.
 */
@Service
@RequiredArgsConstructor
public class ImageService {

    public static final long MAX_SIZE = 5L * 1024 * 1024;   // 5MB

    private final StorageService storageService;

    /** 크기 → 확장자 → 실제 내용 순서로 검사해요. 저장할 때는 실제 내용 기준의 확장자를 붙여요 */
    public ImageUploadResponse upload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
        if (file.getSize() > MAX_SIZE) {   // Spring 설정(max-file-size)이 먼저 막지만 한 번 더
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        if (!ImageType.isAllowedExtension(file.getOriginalFilename())) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
        ImageType type = ImageType.detect(readHead(file))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_FILE_TYPE));

        try (InputStream content = file.getInputStream()) {
            return new ImageUploadResponse(storageService.store(content, type.getExtension()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] readHead(MultipartFile file) {
        try (InputStream content = file.getInputStream()) {
            return content.readNBytes(ImageType.HEAD_SIZE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
