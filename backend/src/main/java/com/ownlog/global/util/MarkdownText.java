package com.ownlog.global.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 마크다운에서 카드용 요약과 첫 이미지를 뽑아요 (P-02, S-03). 저장할 때 한 번만 실행해요. */
public final class MarkdownText {

    private static final int SUMMARY_LENGTH = 150;
    private static final int MAX_URL_LENGTH = 500;
    private static final Pattern FIRST_IMAGE = Pattern.compile("!\\[[^\\]]*]\\(\\s*([^)\\s]+)");

    private MarkdownText() {
    }

    /** ![설명](주소)의 첫 번째 주소. 없거나 500자를 넘으면 null */
    public static String firstImageUrl(String markdown) {
        if (markdown == null) {
            return null;
        }
        Matcher matcher = FIRST_IMAGE.matcher(markdown);
        if (!matcher.find()) {
            return null;
        }
        String url = matcher.group(1);
        return url.length() <= MAX_URL_LENGTH ? url : null;
    }

    /** 마크다운 문법을 걷어 내고 앞 150자. 넘으면 "…"를 붙여요 */
    public static String summarize(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return null;
        }
        String text = markdown
                .replaceAll("(?s)```.*?```", " ")                             // 코드 블록
                .replaceAll("`([^`]*)`", "$1")                                // 인라인 코드 → 글자만
                .replaceAll("\\[\\[\\d{1,18}\\|([^\\]\\n]{1,100})]]", "$1")      // [[번호|글자]] 글 연결 → 글자만 (LK-02)
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", " ")                   // 이미지 제거
                .replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1")                 // 링크 → 글자만
                .replaceAll("<[^>]+>", " ")                                   // HTML 태그
                .replaceAll("(?m)^\\s{0,3}(#{1,6}|>|[-*+]|\\d+\\.)\\s+", "")  // 제목, 인용, 목록 기호
                .replaceAll("[*_~]", "")                                      // 굵게, 기울임, 취소선
                .replaceAll("\\s+", " ")
                .strip();
        return text.length() <= SUMMARY_LENGTH ? text : text.substring(0, SUMMARY_LENGTH) + "…";
    }
}
