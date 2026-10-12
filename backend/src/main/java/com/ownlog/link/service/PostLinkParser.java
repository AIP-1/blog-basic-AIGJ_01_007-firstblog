package com.ownlog.link.service;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 본문에서 [[글번호|표시 글자]] 링크의 글번호를 뽑아요 (LK-04).
 * 코드 블록(```, ~~~)과 인라인 코드(`...`) 안의 링크는 예시일 수 있어서 빼요. 화면(remark)도 같은 규칙이에요.
 */
public final class PostLinkParser {

    public static final int MAX_LINKS = 50;

    private static final Pattern FENCED_CODE = Pattern.compile("(?ms)^\\s*(```|~~~).*?^\\s*\\1");
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`\\n]*`");
    private static final Pattern LINK = Pattern.compile("\\[\\[(\\d{1,18})\\|([^\\]\\n]{1,100})]]");

    private PostLinkParser() {
    }

    /** 나온 순서대로, 중복 없이 최대 50개 */
    public static Set<Long> parse(String markdown) {
        Set<Long> ids = new LinkedHashSet<>();
        if (markdown == null || !markdown.contains("[[")) {
            return ids;
        }
        String text = INLINE_CODE.matcher(FENCED_CODE.matcher(markdown).replaceAll("")).replaceAll("");
        Matcher matcher = LINK.matcher(text);
        while (matcher.find() && ids.size() < MAX_LINKS) {
            ids.add(Long.parseLong(matcher.group(1)));
        }
        return ids;
    }
}
