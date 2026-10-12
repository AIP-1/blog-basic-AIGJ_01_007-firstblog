package com.ownlog.link;

import static org.assertj.core.api.Assertions.assertThat;

import com.ownlog.link.service.PostLinkParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** [[번호|글자]] 파싱 규칙 (LK-04) */
class PostLinkParserTest {

    @Test
    @DisplayName("나온 순서대로 중복 없이 번호를 뽑는다")
    void parsesInOrderWithoutDuplicates() {
        assertThat(PostLinkParser.parse("앞 [[17|JWT 정리]] 중간 [[3|스프링]] 다시 [[17|또]]"))
                .containsExactly(17L, 3L);
    }

    @Test
    @DisplayName("코드 블록과 인라인 코드 안의 링크, 형식이 틀린 링크는 무시한다")
    void ignoresCodeAndMalformed() {
        String md = """
                진짜 [[1|글]]
                ```
                예시 [[2|코드 블록]]
                ```
                `[[3|인라인]]` [[abc|번호 아님]] [[4]] [[5|]] [[ 6|공백]]
                ~~~md
                [[7|물결 코드]]
                ~~~
                마지막 [[8|끝]]
                """;
        assertThat(PostLinkParser.parse(md)).containsExactly(1L, 8L);
    }

    @Test
    @DisplayName("최대 50개까지만, 빈 본문은 빈 목록")
    void limitsAndEmpty() {
        StringBuilder md = new StringBuilder();
        for (int i = 1; i <= 60; i++) {
            md.append("[[").append(i).append("|글]] ");
        }
        assertThat(PostLinkParser.parse(md.toString())).hasSize(50);
        assertThat(PostLinkParser.parse(null)).isEmpty();
        assertThat(PostLinkParser.parse("링크 없음")).isEmpty();
    }
}
