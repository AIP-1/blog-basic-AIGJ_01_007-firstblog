package com.ownlog.universe;

import static org.assertj.core.api.Assertions.assertThat;

import com.ownlog.global.util.MarkdownText;
import com.ownlog.universe.service.Relatedness;
import com.ownlog.universe.service.Relatedness.Doc;
import com.ownlog.universe.service.Relatedness.Key;
import com.ownlog.universe.service.Relatedness.Options;
import com.ownlog.universe.service.Relatedness.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 관련 있는 글 계산 단위 테스트 (GR-02): 단어 나누기, TF-IDF 코사인, 흔한 태그 무시, 상위 K개 */
class RelatednessTest {

    /** GraphService와 같은 값 (기준 0.3, 별마다 3개) */
    static final Options OPTIONS = new Options(0.3, 3, 1000);

    @Test
    @DisplayName("한글은 2글자 조각으로 나눠서 조사가 달라도 같은 조각이 생긴다, 영문은 소문자, 한 글자·숫자는 버린다")
    void tokenizeKorean() {
        assertThat(Relatedness.tokenize("여행을")).containsExactly("여행", "행을");
        assertThat(Relatedness.tokenize("여행은")).contains("여행");
        assertThat(Relatedness.tokenize("부산 여행 1일차: 해운대")).containsExactly("부산", "여행", "일차", "해운", "운대");
        assertThat(Relatedness.tokenize("Spring에서 JWT를 a 19")).containsExactly("spring", "jwt");
        assertThat(Relatedness.tokenize("책 더 잘")).isEmpty();
        assertThat(Relatedness.tokenize(null)).isEmpty();
    }

    @Test
    @DisplayName("조사만 다른 제목끼리는 관련 있고, 근거 단어는 원래 낱말의 겹치는 부분으로 보여준다")
    void particlesDoNotBreakMatching() {
        List<Pair> pairs = Relatedness.compute(List.of(
                new Doc(1, "제주도 여행을 준비하며", null, List.of()),
                new Doc(2, "제주도 여행은 처음이라", null, List.of()),
                new Doc(3, "김치찌개 황금 비율", null, List.of())), Set.of(), OPTIONS);

        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0).source()).isEqualTo(1);
        assertThat(pairs.get(0).target()).isEqualTo(2);
        assertThat(pairs.get(0).keywords()).contains("제주도");
        assertThat(pairs.get(0).score()).isBetween(0.3, 1.0);
    }

    @Test
    @DisplayName("모든 글에 붙은 흔한 태그 하나만 같으면 선이 없고, 드문 태그를 함께 쓰면 선과 근거 태그가 있다")
    void commonTagIgnoredRareTagConnects() {
        List<Doc> docs = new ArrayList<>();
        String[] places = {"강릉", "속초", "경주", "전주", "여수", "통영", "안동", "춘천"};
        for (int i = 0; i < places.length; i++) {
            docs.add(new Doc(i + 1, places[i] + " 다녀옴", null, List.of("여행")));
        }
        docs.add(new Doc(100, "바다 사진", null, List.of("여행", "필름카메라")));
        docs.add(new Doc(101, "골목 사진", null, List.of("여행", "필름카메라")));

        List<Pair> pairs = Relatedness.compute(docs, Set.of(), OPTIONS);

        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0).tags()).containsExactly("필름카메라");
        assertThat(pairs.get(0).tags()).doesNotContain("여행");
    }

    @Test
    @DisplayName("별마다 가장 가까운 K개까지만: 똑같이 닮은 글 8개라도 28쌍이 모두 이어지지 않는다")
    void topKPerNode() {
        List<Doc> docs = new ArrayList<>();
        IntStream.rangeClosed(1, 8).forEach(i -> docs.add(new Doc(i, "쿠버네티스 배포 " + i, null, List.of("쿠버네티스"))));
        IntStream.rangeClosed(11, 22).forEach(i -> docs.add(new Doc(i, "점심 메뉴 " + i, null, List.of("일상" + i))));

        List<Pair> pairs = Relatedness.compute(docs, Set.of(), OPTIONS);
        Map<Long, Integer> degree = new HashMap<>();
        pairs.forEach(p -> {
            degree.merge(p.source(), 1, Integer::sum);
            degree.merge(p.target(), 1, Integer::sum);
        });

        // 쿠버네티스 글 8개는 서로 28쌍이 될 수 있지만, 각자 고른 상위 3개의 합집합만 남아요 (점수가 같으면 번호 순)
        assertThat(pairs).hasSize(18);
        assertThat(degree.keySet()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        // "점심 메뉴" 글 12개는 20개 중 절반 넘게 나오는 단어뿐이라 선이 없어요
        assertThat(pairs).allMatch(p -> p.source() <= 8 && p.target() <= 8);
    }

    @Test
    @DisplayName("작은 블로그: 글 4개가 모두 같은 태그 하나뿐이면 선이 없다 (흔한 단어 제외는 별 4개부터)")
    void smallBlogCommonTag() {
        List<Doc> docs = List.of(
                new Doc(1, "부산 해운대", null, List.of("여행")),
                new Doc(2, "제주 한 달 살기", null, List.of("여행")),
                new Doc(3, "도쿄 3박 4일", null, List.of("여행")),
                new Doc(4, "강릉 커피 거리", null, List.of("여행")));
        assertThat(Relatedness.compute(docs, Set.of(), OPTIONS)).isEmpty();
    }

    @Test
    @DisplayName("[[링크]]로 이미 이어진 쌍은 관련 선을 만들지 않는다")
    void skipsLinkedPairs() {
        List<Doc> docs = List.of(
                new Doc(1, "JWT 인증 정리", null, List.of("JWT", "보안")),
                new Doc(2, "Spring Security 적용기", null, List.of("JWT", "보안")));
        assertThat(Relatedness.compute(docs, Set.of(), OPTIONS)).hasSize(1);
        assertThat(Relatedness.compute(docs, Set.of(Key.of(2, 1)), OPTIONS)).isEmpty();
    }

    /**
     * 시연 데이터(scripts/seed-demo.mjs)의 서비스 전체 우주(/api/universe)와 같은 글 24개로 예전 방식과 비교해요.
     * 숫자는 설명서(문서 저장소 ownlog-docs의 docs/guide/기록-우주-연관도.md) "트러블슈팅" 결과에 적었어요.
     */
    @Test
    @DisplayName("시연 데이터: 태그 하나만 같아도 잇던 예전 방식보다 선이 줄고, 흔한 태그 하나뿐인 쌍은 빠진다")
    void demoDataBeforeAfter() {
        List<Doc> docs = demoDocs();
        Set<Key> linked = demoLinks();

        int before = 0;
        for (int i = 0; i < docs.size(); i++) {
            for (int j = i + 1; j < docs.size(); j++) {
                Set<String> shared = lower(docs.get(i).tags());
                shared.retainAll(lower(docs.get(j).tags()));
                if (!shared.isEmpty()) {
                    before++;
                }
            }
        }
        List<Pair> after = Relatedness.compute(docs, linked, OPTIONS);
        System.out.printf("[연관도] 글 %d개, 링크 %d개, 예전 태그 선 %d개 → 관련 선 %d개%n",
                docs.size(), linked.size(), before, after.size());
        after.forEach(p -> System.out.printf("  %s ~ %s  %.2f  %s %s%n",
                title(docs, p.source()), title(docs, p.target()), p.score(), p.tags(), p.keywords()));

        for (long[] blog : new long[][] {{1, 10}, {11, 15}, {20, 22}, {31, 32}, {41, 44}}) {
            List<Doc> mine = docs.stream().filter(d -> d.id() >= blog[0] && d.id() <= blog[1]).toList();
            int old = 0;
            for (int i = 0; i < mine.size(); i++) {
                for (int j = i + 1; j < mine.size(); j++) {
                    Set<String> shared = lower(mine.get(i).tags());
                    shared.retainAll(lower(mine.get(j).tags()));
                    if (!shared.isEmpty()) {
                        old++;
                    }
                }
            }
            List<Pair> now = Relatedness.compute(mine, linked, OPTIONS);
            System.out.printf("[블로그 %d~%d] 글 %d개, 예전 %d개 → %d개%n", blog[0], blog[1], mine.size(), old, now.size());
            now.forEach(p -> System.out.printf("  %s ~ %s  %.2f  %s %s%n",
                    title(docs, p.source()), title(docs, p.target()), p.score(), p.tags(), p.keywords()));
        }

        assertThat(after).hasSizeLessThan(before);
        // 부산 2일차 ~ 부산식 돼지국밥: "부산" 태그 + 함께 나온 단어로 이어져요
        assertThat(after).anyMatch(p -> Key.of(p.source(), p.target()).equals(Key.of(12, 20)) && p.tags().contains("부산"));
        // 부산 2일차 ~ 제주, 제주 ~ 도쿄: "여행" 태그 하나만 같아요 → 선 없음
        assertThat(after).noneMatch(p -> Key.of(p.source(), p.target()).equals(Key.of(12, 13)));
        assertThat(after).noneMatch(p -> Key.of(p.source(), p.target()).equals(Key.of(13, 14)));
        // 부산 1일차 ~ 부산 2일차는 [[링크]]로 이미 이어져서 관련 선 대신 링크 선만 있어요
        assertThat(after).noneMatch(p -> Key.of(p.source(), p.target()).equals(Key.of(11, 12)));
        // 여행 블로그 하나만 보면(글 5개 중 4개가 "여행") 예전 8개였던 태그 선이 모두 사라져요
        List<Doc> trip = docs.stream().filter(d -> d.id() >= 11 && d.id() <= 15).toList();
        assertThat(Relatedness.compute(trip, linked, OPTIONS)).isEmpty();
    }

    /** 서비스 전체 우주 최대치(별 500개)에서도 금방 끝나고, 별마다 상위 K개라 선이 폭발하지 않아요 */
    @Test
    @DisplayName("별 500개: 1초 안에 끝나고 선은 별 수 × K 이하, 흔한 태그로 생기던 선은 사라진다")
    void fiveHundredNodes() {
        Random random = new Random(42);
        String[] broad = {"여행", "일상", "개발", "요리", "운동"};
        String[] words = {"부산", "제주", "서울", "강릉", "도쿄", "스프링", "리액트", "도커", "파스타", "김치", "러닝", "요가",
            "카페", "바다", "산책", "독서", "영화", "음악", "사진", "캠핑"};
        List<Doc> docs = new ArrayList<>();
        for (int i = 1; i <= 500; i++) {
            String title = words[random.nextInt(words.length)] + " " + words[random.nextInt(words.length)] + " 기록";
            docs.add(new Doc(i, title, title + "에 대해 적어 봤어요.",
                    List.of(broad[random.nextInt(broad.length)], "주제" + random.nextInt(100), "주제" + random.nextInt(100))));
        }
        int before = 0;
        for (int i = 0; i < docs.size(); i++) {
            for (int j = i + 1; j < docs.size(); j++) {
                Set<String> shared = lower(docs.get(i).tags());
                shared.retainAll(lower(docs.get(j).tags()));
                before += shared.isEmpty() ? 0 : 1;
            }
        }

        long start = System.nanoTime();
        List<Pair> after = Relatedness.compute(docs, Set.of(), new Options(0.3, 3, 1500));
        long millis = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("[연관도 500] 예전 태그 선 %d개(상한 1,500에서 잘림) → 관련 선 %d개, %dms%n", before, after.size(), millis);

        assertThat(millis).isLessThan(1000);
        assertThat(after).hasSizeLessThanOrEqualTo(500 * 3);
        // 넓은 태그(여행·일상…, 글 100개쯤에 붙음) 하나만으로 이어진 선은 없어요: 드문 태그나 함께 나온 단어가 꼭 있어요
        List<String> broadTags = List.of(broad);
        assertThat(after).allMatch(p -> p.tags().stream().anyMatch(t -> !broadTags.contains(t)) || !p.keywords().isEmpty());
    }

    private static Set<String> lower(List<String> tags) {
        Set<String> out = new HashSet<>();
        tags.forEach(t -> out.add(t.toLowerCase(Locale.ROOT)));
        return out;
    }

    private static String title(List<Doc> docs, long id) {
        return docs.stream().filter(d -> d.id() == id).findFirst().orElseThrow().title();
    }

    private static Doc doc(long id, String title, String body, String... tags) {
        return new Doc(id, title, MarkdownText.summarize(body), List.of(tags));
    }

    /** seed-demo.mjs의 공개 글 (봉인 캡슐·비공개·비공개 카테고리 제외). 본문 끝의 "함께 보면 좋은 기록"도 요약에 들어가요 */
    static List<Doc> demoDocs() {
        String see = "\n\n### 함께 보면 좋은 기록\n";
        return List.of(
                doc(1, "Java 기초 문법 정리", "## 변수와 타입\n\n자바는 **정적 타입** 언어예요.\n\n```java\nint count = 10;\n```\n\n- 기본형: `int`, `long`, `boolean`\n- 참조형: `String`, 배열, 클래스", "Java", "기초"),
                doc(2, "JVM 메모리 구조", "## Heap과 Stack\n\n객체는 Heap, 지역 변수는 Stack에 저장돼요.\n\n> GC는 Heap의 쓰지 않는 객체를 정리해요." + see + "- [[1|Java 기초 문법 정리]]", "Java", "JVM"),
                doc(3, "Spring Boot 시작하기", "## 프로젝트 만들기\n\nstart.spring.io에서 의존성을 고르고 시작해요.\n\n```bash\n./gradlew bootRun\n```" + see + "- [[1|Java 기초 문법 정리]]", "Spring", "Java"),
                doc(4, "JPA 연관관계 정리", "## 연관관계의 주인\n\n외래키를 가진 쪽이 주인이에요. `mappedBy`는 주인이 아닌 쪽에 써요.\n\n**추가:** 지연 로딩(LAZY)을 기본으로 써요." + see + "- [[3|Spring Boot 시작하기]]", "Spring", "JPA"),
                doc(5, "JWT 인증 정리", "## 토큰 구조\n\nHeader.Payload.Signature 세 부분으로 나뉘어요.\n\n본문 예시 코드에 있는 `[[1|이건 링크가 아니에요]]`는 코드라서 링크로 바뀌지 않아요." + see + "- [[3|Spring Boot 시작하기]]", "Spring", "JWT", "보안"),
                doc(6, "Spring Security 적용기", "## 필터 체인\n\nJWT 필터를 `UsernamePasswordAuthenticationFilter` 앞에 넣었어요." + see + "- [[5|JWT 인증 정리]]\n- [[3|Spring Boot 시작하기]]", "Spring", "JWT", "보안"),
                doc(7, "React 19 훑어보기", "## useEffectEvent\n\n이펙트 안에서 최신 값을 읽되, 의존성에는 넣지 않을 수 있어요.", "React", "프론트"),
                doc(8, "Vite로 프로젝트 만들기", "## 개발 서버\n\n`npm run dev` 한 줄이면 끝이에요. 프록시로 `/api`를 백엔드에 넘겨요." + see + "- [[7|React 19 훑어보기]]", "React", "Vite", "프론트"),
                doc(9, "OwnLog 2주 개발 회고", "## 잘한 것\n\n- 기획부터 배포까지 단계별로 진행\n- 테스트를 먼저 챙겼어요\n\n## 아쉬운 것\n\n- 처음에 일정이 조금 밀렸어요" + see + "- [[6|Spring Security 적용기]]\n- [[4|JPA 연관관계 정리]]\n- [[8|Vite로 프로젝트 만들기]]\n- [[31|『클린 코드』를 읽고]]\n- [[42|기록하는 습관 만들기]]", "회고", "Spring", "React"),
                doc(10, "3분 뒤에 열리는 기록", "## 3분 전의 나에게서\n\n이 글은 발행하고 3분 뒤에 열렸어요. 메인 \"최근 열린 타임캡슐\"에서도 보여요. ✨" + see + "- [[9|OwnLog 2주 개발 회고]]", "타임캡슐"),
                doc(11, "부산 여행 1일차: 해운대", "## 해운대 산책\n\n아침 바다가 정말 예뻤어요. 🌊\n\n1. 해운대 해수욕장\n2. 동백섬\n3. 더베이101 야경", "부산", "여행", "바다"),
                doc(12, "부산 여행 2일차: 감천문화마을", "## 알록달록한 골목\n\n어린왕자 동상 앞은 줄이 길었어요." + see + "- [[11|부산 여행 1일차: 해운대]]", "부산", "여행"),
                doc(13, "제주 한 달 살기 준비", "## 준비물\n\n- 렌터카 예약\n- 숙소 (한 달 단위가 저렴해요)", "제주", "여행"),
                doc(14, "도쿄 3박 4일 코스", "## 시부야 → 아사쿠사\n\n지하철 패스를 꼭 사세요.", "도쿄", "여행", "해외"),
                doc(15, "부산에서 먹은 것들", "## 돼지국밥, 밀면, 씨앗호떡\n\n집에서도 만들 수 있다길래 레시피를 찾아봤어요." + see + "- [[12|부산 여행 2일차: 감천문화마을]]\n- [[20|부산식 돼지국밥 집에서 끓이기]]", "부산", "맛집"),
                doc(20, "부산식 돼지국밥 집에서 끓이기", "## 재료\n\n- 돼지 앞다리살 600g\n- 사골 육수\n\n부산 여행에서 먹은 맛을 떠올리며 만들었어요." + see + "- [[11|부산 여행 1일차: 해운대]]", "부산", "국밥", "레시피"),
                doc(21, "집에서 라멘 만들기", "## 차슈부터\n\n삼겹살을 말아서 간장에 졸여요.", "라멘", "레시피"),
                doc(22, "김치찌개 황금 비율", "## 비율\n\n김치 2 : 돼지고기 1 : 물 3", "김치찌개", "레시피"),
                doc(31, "『클린 코드』를 읽고", "## 함수는 한 가지만\n\n> 깨끗한 코드는 한 가지를 제대로 한다.\n\n코드파도님의 자바 정리 글과 같이 읽으면 좋아요." + see + "- [[1|Java 기초 문법 정리]]", "독서", "개발", "Java"),
                doc(32, "『데미안』 다시 읽기", "## 알을 깨고 나오기\n\n스무 살에 읽었을 때와 느낌이 달라요.", "독서", "고전"),
                doc(41, "아침 러닝 30일 챌린지", "## 첫 주가 제일 힘들어요\n\n5km를 35분 → 29분으로 줄였어요.", "러닝", "습관", "운동"),
                doc(42, "기록하는 습관 만들기", "## 매일 한 줄\n\n잔디가 끊기지 않게 매일 짧게라도 써요. 🌱" + see + "- [[41|아침 러닝 30일 챌린지]]\n- [[3|Spring Boot 시작하기]]", "습관", "회고"),
                doc(43, "요즘 하는 인디 게임", "## 하데스 2\n\n한 판만 하려다 새벽 2시...", "게임", "인디"),
                doc(44, "이번 주 본 영화", "## 인사이드 아웃 2\n\n불안이 귀여웠어요.", "영화"));
    }

    static Set<Key> demoLinks() {
        long[][] links = {{2, 1}, {3, 1}, {4, 3}, {5, 3}, {6, 5}, {6, 3}, {8, 7}, {9, 6}, {9, 4}, {9, 8}, {9, 31}, {9, 42},
            {10, 9}, {12, 11}, {15, 12}, {15, 20}, {20, 11}, {31, 1}, {42, 41}, {42, 3}};
        Set<Key> out = new HashSet<>();
        for (long[] l : links) {
            out.add(Key.of(l[0], l[1]));
        }
        return out;
    }
}
