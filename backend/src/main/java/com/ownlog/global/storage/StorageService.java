package com.ownlog.global.storage;

import java.io.InputStream;

/**
 * 이미지 저장소. 지금은 서버 디스크(LocalStorageService)이고,
 * 배포 B안(PaaS)으로 가면 Cloudinary 같은 구현체로 바꿔요 (요구사항 8장 결정).
 */
public interface StorageService {

    /** 내용을 저장하고 "/uploads/..." 주소를 돌려줘요. 파일 이름은 저장소가 정해요 (사용자가 올린 이름은 쓰지 않아요) */
    String store(InputStream content, String extension);

    /** "/uploads/..." 주소의 파일을 지워요. 없거나 우리 저장소 주소가 아니면 아무것도 안 해요 */
    void delete(String url);
}
