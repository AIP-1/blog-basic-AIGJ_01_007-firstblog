package com.ownlog.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 카드 요약과 썸네일 자동 지정 (P-02, S-03). DB 없이 빠르게 돌아가는 단위 테스트예요. */
class MarkdownTextTest {

    @Test
    @DisplayName("제목, 굵게, 링크, 이미지, 코드 문법을 걷어 낸다")
    void stripsMarkdown() {
        String markdown = """
                ## 연관관계의 주인

                **외래 키**를 가진 쪽이 [주인](https://example.com)이에요.
                ![diagram](/uploads/a.png)

                ```java
                @ManyToOne Blog blog;
                ```
                - 목록은 `LAZY`로
                """;

        assertThat(MarkdownText.summarize(markdown))
                .isEqualTo("연관관계의 주인 외래 키를 가진 쪽이 주인이에요. 목록은 LAZY로");
    }

    @Test
    @DisplayName("150자를 넘으면 자르고 …를 붙인다")
    void truncates() {
        String summary = MarkdownText.summarize("가".repeat(200));

        assertThat(summary).hasSize(151).endsWith("…");
    }

    @Test
    @DisplayName("본문의 첫 번째 이미지 주소를 찾고, 없으면 null")
    void firstImage() {
        assertThat(MarkdownText.firstImageUrl("글 ![a](/uploads/1.png) 그리고 ![b](/uploads/2.png)"))
                .isEqualTo("/uploads/1.png");
        assertThat(MarkdownText.firstImageUrl("이미지 없는 글")).isNull();
        assertThat(MarkdownText.summarize("   ")).isNull();
    }
}
