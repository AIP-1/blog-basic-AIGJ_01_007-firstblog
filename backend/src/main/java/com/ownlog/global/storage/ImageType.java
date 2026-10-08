package com.ownlog.global.storage;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;

/**
 * 업로드를 허용하는 이미지 형식 (P-03). 파일 앞부분 바이트(매직 넘버)로 판별해요.
 * SVG는 안에 스크립트를 넣을 수 있어서 허용하지 않아요.
 */
@Getter
public enum ImageType {

    JPEG("jpg", Set.of("jpg", "jpeg")),
    PNG("png", Set.of("png")),
    GIF("gif", Set.of("gif")),
    WEBP("webp", Set.of("webp"));

    /** 판별에 필요한 앞부분 바이트 수 (WEBP가 12바이트로 가장 길어요) */
    public static final int HEAD_SIZE = 12;

    /** 저장할 때 붙일 확장자 */
    private final String extension;

    /** 사용자가 올린 파일 이름에 허용하는 확장자 */
    private final Set<String> allowedExtensions;

    ImageType(String extension, Set<String> allowedExtensions) {
        this.extension = extension;
        this.allowedExtensions = allowedExtensions;
    }

    /** 파일 앞 12바이트로 형식을 알아내요. 허용 형식이 아니면 빈 값 */
    public static Optional<ImageType> detect(byte[] head) {
        if (startsWith(head, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(head, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (asciiAt(head, 0, "GIF87a") || asciiAt(head, 0, "GIF89a")) {
            return Optional.of(GIF);
        }
        if (asciiAt(head, 0, "RIFF") && asciiAt(head, 8, "WEBP")) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    /** 파일 이름의 확장자가 허용 목록에 있나 (대소문자 무시) */
    public static boolean isAllowedExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return false;
        }
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).anyMatch(type -> type.allowedExtensions.contains(extension));
    }

    private static boolean startsWith(byte[] head, int... expected) {
        if (head.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((head[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean asciiAt(byte[] head, int offset, String expected) {
        byte[] bytes = expected.getBytes(StandardCharsets.US_ASCII);
        return head.length >= offset + bytes.length
                && Arrays.equals(head, offset, offset + bytes.length, bytes, 0, bytes.length);
    }
}
