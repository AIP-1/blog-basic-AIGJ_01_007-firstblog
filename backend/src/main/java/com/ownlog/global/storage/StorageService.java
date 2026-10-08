package com.ownlog.global.storage;

/**
 * 이미지 저장소. 지금은 서버 디스크(LocalStorageService)이고,
 * 배포 B안(PaaS)으로 가면 Cloudinary 같은 구현체로 바꿔요 (요구사항 8장 결정).
 * 업로드(store)는 API 11.1을 만들 때 추가해요.
 */
public interface StorageService {

    /** "/uploads/..." 주소의 파일을 지워요. 없거나 우리 저장소 주소가 아니면 아무것도 안 해요 */
    void delete(String url);
}
