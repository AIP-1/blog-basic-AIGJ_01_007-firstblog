package com.ownlog.universe.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 기록 우주의 "관련 있는 글" 선 계산 (GR-02). 외부 API·DB 확장 없이 앱 안에서 계산해요.
 *
 * <p>글 하나를 "단어 → 중요도" 숫자 묶음(희소 벡터)으로 바꾸고 두 글의 방향이 얼마나 같은지(코사인 유사도)를 재요.
 * 중요도는 TF-IDF예요: 그 글에 자주 나올수록(TF), 다른 글에는 드물수록(IDF) 커요. 그래서 거의 모든 글에 붙은
 * "여행" 같은 태그 하나로는 선이 생기지 않고, 드문 태그·단어를 함께 가진 글끼리만 이어져요.
 * 설명서: 문서 저장소 ownlog-docs의 docs/guide/기록-우주-연관도.md
 *
 * <p>입력은 보는 사람이 볼 수 있는 글의 제목·요약(150자)·태그뿐이에요. 본문 전체는 읽지 않고(쿼리가 가벼워요),
 * 방문자에게 잠긴 캡슐은 GraphService가 아예 넣지 않아서 단어 통계(IDF)에도 섞이지 않아요.
 */
public final class Relatedness {

    /** 제목과 태그는 글의 주제를 요약한 것이라 요약 본문보다 두 배로 쳐요 */
    static final double TITLE_WEIGHT = 2;
    static final double TAG_WEIGHT = 2;
    static final double SUMMARY_WEIGHT = 1;
    /**
     * 별이 이만큼 이상이면, 절반 넘는 글에 나오는 태그·단어는 구분력이 없어서 버려요.
     * 글 5개 중 4개에 "여행"이 붙은 여행 블로그에서 "여행" 하나로 모든 글이 이어지던 문제를 막아요 (별이 2~3개면 절반이 1개라 버리지 않아요).
     */
    static final int MIN_NODES_FOR_COMMON_CUT = 4;
    static final double COMMON_RATIO = 0.5;
    static final int MAX_REASON_TAGS = 5;
    static final int MAX_KEYWORDS = 3;

    /** 한국어 해요체 글에서 거의 모든 글에 나오는 어미·조사 조각(2글자)과 영어 기능어. IDF가 대부분 걸러 주지만 별이 적을 때를 위해 둬요 */
    private static final Set<String> STOP = Set.of(
            "에서", "으로", "하고", "해요", "어요", "아요", "에요", "예요", "이에", "했어", "었어", "았어", "있어", "없어",
            "니다", "습니", "합니", "입니", "하는", "하기", "해서", "는데", "지만", "그리", "리고", "그래", "래서", "하지",
            "이런", "저런", "그런", "것을", "것이", "정말", "너무", "진짜", "우리", "나는", "제가", "저는", "있는", "없는",
            "같은", "이다", "했다", "있다", "한다", "하다", "되는", "된다", "에게", "까지", "부터", "보다", "처럼",
            "the", "and", "for", "with", "you", "are", "this", "that", "from", "was", "but", "not", "have", "has",
            "its", "into", "can", "will", "of", "to", "in", "is", "on", "at", "an", "or", "be", "by", "as");

    /** 계산에 넣을 글 한 개 (볼 수 있는 글만 넣어요) */
    public record Doc(long id, String title, String summary, List<String> tags) {
    }

    /** 순서 없는 글 쌍 (a < b). [[링크]]로 이미 이어진 쌍을 건너뛸 때 써요 */
    public record Key(long a, long b) {
        public static Key of(long x, long y) {
            return x < y ? new Key(x, y) : new Key(y, x);
        }
    }

    /** 관련 있는 글 쌍 (source < target): score 0~1, 근거(같이 붙은 드문 태그, 함께 나온 단어) */
    public record Pair(long source, long target, double score, List<String> tags, List<String> keywords) {
    }

    /** minScore: 이보다 낮으면 선을 긋지 않아요. topK: 별마다 가장 가까운 K개까지만. maxEdges: 전체 상한 */
    public record Options(double minScore, int topK, int maxEdges) {
    }

    private Relatedness() {
    }

    // ---------------------------------------------------------------------------------
    // 1. 단어 나누기
    // ---------------------------------------------------------------------------------

    /**
     * 글자를 단어(term)로 나눠요. 영문·숫자는 소문자 단어 그대로, 한글은 2글자씩 겹쳐 자른 조각(bigram)으로요.
     * 한국어는 "여행을/여행은/여행에서"처럼 조사가 붙어서 단어 그대로는 서로 다른 말이 돼요.
     * 2글자씩 자르면 셋 다 "여행" 조각을 가져서 형태소 분석기 없이도 맞춰 볼 수 있어요.
     */
    public static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        scan(text, (term, surface) -> out.add(term));
        return out;
    }

    private interface TermSink {
        void accept(String term, String surface);
    }

    private static void scan(String text, TermSink sink) {
        if (text == null) {
            return;
        }
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            int j = i + 1;
            if (isHangul(c)) {
                while (j < n && isHangul(text.charAt(j))) {
                    j++;
                }
                String word = text.substring(i, j);
                // 한 글자 낱말("더", "잘", "책")은 조사·부사가 대부분이라 버려요 (설명서 "한계" 참고)
                for (int k = 0; k + 2 <= word.length(); k++) {
                    String gram = word.substring(k, k + 2);
                    if (!STOP.contains(gram)) {
                        sink.accept(gram, word);
                    }
                }
            } else if (Character.isLetterOrDigit(c)) {
                // 영문·숫자 덩어리는 한글을 만나면 끊어요 ("Spring에서" → "spring" + "에서")
                while (j < n && Character.isLetterOrDigit(text.charAt(j)) && !isHangul(text.charAt(j))) {
                    j++;
                }
                String word = text.substring(i, j);
                String term = word.toLowerCase(Locale.ROOT);
                // 한 글자와 숫자만 있는 것("19", "2026")은 주제를 말해 주지 않아서 버려요
                if (term.length() >= 2 && !term.chars().allMatch(Character::isDigit) && !STOP.contains(term)) {
                    sink.accept(term, word);
                }
            }
            i = j;
        }
    }

    private static boolean isHangul(char c) {
        return c >= '가' && c <= '힣';
    }

    // ---------------------------------------------------------------------------------
    // 2. 관련 있는 쌍 계산
    // ---------------------------------------------------------------------------------

    /**
     * 관련 있는 글 쌍을 점수 높은 순으로 줘요.
     * 점수 = max(태그 점수, 글자 점수). 태그는 글쓴이가 직접 고른 주제라 하나만 강해도 충분하고,
     * 태그가 없는 글도 많아서 평균을 내면 태그 없는 글이 늘 손해를 봐요. 그래서 둘 중 큰 쪽을 써요.
     *
     * @param skip 이미 [[링크]]로 이어진 쌍: 선이 겹치지 않게 건너뛰어요
     */
    public static List<Pair> compute(List<Doc> docs, Set<Key> skip, Options options) {
        int n = docs.size();
        if (n < 2) {
            return List.of();
        }
        int commonLimit = n >= MIN_NODES_FOR_COMMON_CUT ? (int) Math.floor(n * COMMON_RATIO) : Integer.MAX_VALUE;

        // 2-1. 글마다 단어 빈도(TF)와 태그 목록
        List<Map<String, Double>> tf = new ArrayList<>(n);
        List<Map<String, String>> surface = new ArrayList<>(n);
        List<Set<String>> tagKeys = new ArrayList<>(n);
        Map<String, String> tagName = new HashMap<>();
        for (Doc doc : docs) {
            Map<String, Double> counts = new HashMap<>();
            Map<String, String> words = new HashMap<>();
            scan(doc.title(), (t, s) -> add(counts, words, t, s, TITLE_WEIGHT));
            scan(doc.summary(), (t, s) -> add(counts, words, t, s, SUMMARY_WEIGHT));
            Set<String> keys = new LinkedHashSet<>();
            for (String tag : doc.tags() == null ? List.<String>of() : doc.tags()) {
                scan(tag, (t, s) -> add(counts, words, t, s, TAG_WEIGHT));
                String key = tag.toLowerCase(Locale.ROOT);
                keys.add(key);
                tagName.putIfAbsent(key, tag);
            }
            tf.add(counts);
            surface.add(words);
            tagKeys.add(keys);
        }

        // 2-2. 몇 개의 글에 나오는지(DF) → IDF. 절반 넘게 나오는 것은 버려요
        Map<String, Integer> termDf = documentFrequency(tf.stream().map(Map::keySet).toList());
        Map<String, Integer> tagDf = documentFrequency(tagKeys);

        // 2-3. 글 벡터(단어 → TF×IDF)와 길이, 단어별 "이 단어를 가진 글" 목록(역색인)
        List<Map<String, Double>> vectors = new ArrayList<>(n);
        double[] norm = new double[n];
        Map<String, List<Integer>> postings = new HashMap<>();
        for (int i = 0; i < n; i++) {
            Map<String, Double> vector = new HashMap<>();
            for (var e : tf.get(i).entrySet()) {
                int df = termDf.get(e.getKey());
                if (df > commonLimit) {
                    continue;
                }
                double w = e.getValue() * idf(n, df);
                vector.put(e.getKey(), w);
                norm[i] += w * w;
                if (df > 1) {
                    postings.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(i);
                }
            }
            norm[i] = Math.sqrt(norm[i]);
            vectors.add(vector);
        }

        // 2-4. 두 글이 함께 가진 단어만 곱해서 더해요 (모든 단어 × 모든 쌍을 돌지 않아서 500개도 금방 끝나요)
        double[] dot = new double[n * n];
        for (var e : postings.entrySet()) {
            List<Integer> list = e.getValue();
            for (int x = 0; x < list.size(); x++) {
                int i = list.get(x);
                double wi = vectors.get(i).get(e.getKey());
                for (int y = x + 1; y < list.size(); y++) {
                    int j = list.get(y);
                    dot[i * n + j] += wi * vectors.get(j).get(e.getKey());
                }
            }
        }

        // 2-5. 태그 점수: IDF로 무게를 준 자카드 (함께 가진 태그 무게 ÷ 둘 중 하나라도 가진 태그 무게)
        double[] tagSum = new double[n];
        Map<String, List<Integer>> tagPostings = new HashMap<>();
        for (int i = 0; i < n; i++) {
            for (String key : tagKeys.get(i)) {
                int df = tagDf.get(key);
                if (df > commonLimit) {
                    continue;
                }
                tagSum[i] += idf(n, df);
                if (df > 1) {
                    tagPostings.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
                }
            }
        }
        double[] tagShared = new double[n * n];
        for (var e : tagPostings.entrySet()) {
            double w = idf(n, tagDf.get(e.getKey()));
            List<Integer> list = e.getValue();
            for (int x = 0; x < list.size(); x++) {
                for (int y = x + 1; y < list.size(); y++) {
                    tagShared[list.get(x) * n + list.get(y)] += w;
                }
            }
        }

        // 2-6. 후보: 점수가 기준 이상이고 [[링크]]로 이미 이어지지 않은 쌍
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                int k = i * n + j;
                double cosine = dot[k] == 0 ? 0 : dot[k] / (norm[i] * norm[j]);
                double shared = tagShared[k];
                double jaccard = shared == 0 ? 0 : shared / (tagSum[i] + tagSum[j] - shared);
                double score = Math.clamp(Math.max(cosine, jaccard), 0.0, 1.0);
                if (score >= options.minScore() && !skip.contains(Key.of(docs.get(i).id(), docs.get(j).id()))) {
                    candidates.add(new Candidate(i, j, score));
                }
            }
        }

        // 2-7. 별마다 가장 가까운 K개: 한쪽이라도 서로를 K개 안에 꼽으면 남겨요
        //      (양쪽 모두를 요구하면 인기 많은 글 주변의 작은 글이 외톨이가 되기 쉬워요)
        Comparator<Candidate> strongest = Comparator.comparingDouble(Candidate::score).reversed()
                .thenComparingInt(Candidate::i).thenComparingInt(Candidate::j);
        candidates.sort(strongest);
        int[] picked = new int[n];
        List<Candidate> kept = new ArrayList<>();
        // 점수 높은 순으로 보면서 아직 K개를 다 채우지 않은 쪽이 있으면 고르는 것은 "각자의 상위 K개 합집합"과 같아요
        for (Candidate c : candidates) {
            if (picked[c.i()] < options.topK() || picked[c.j()] < options.topK()) {
                kept.add(c);
            }
            picked[c.i()]++;
            picked[c.j()]++;
            if (kept.size() >= options.maxEdges()) {
                break;
            }
        }

        // 2-8. 근거: 함께 붙은 드문 태그(드문 것부터), 함께 나온 단어(곱이 큰 것부터)
        List<Pair> out = new ArrayList<>(kept.size());
        for (Candidate c : kept) {
            int i = c.i();
            int j = c.j();
            List<String> tags = tagKeys.get(i).stream()
                    .filter(key -> tagKeys.get(j).contains(key) && tagDf.get(key) <= commonLimit)
                    .sorted(Comparator.<String>comparingInt(tagDf::get).thenComparing(Comparator.naturalOrder()))
                    .limit(MAX_REASON_TAGS)
                    .map(tagName::get)
                    .toList();
            // 방향이 없는 선이라 작은 번호를 source로 둬요 (응답이 늘 같은 모양이 되게)
            Key key = Key.of(docs.get(i).id(), docs.get(j).id());
            out.add(new Pair(key.a(), key.b(), Math.round(c.score() * 100) / 100.0, tags,
                    keywords(vectors.get(i), vectors.get(j), surface.get(i), surface.get(j), tags)));
        }
        return out;
    }

    private record Candidate(int i, int j, double score) {
    }

    private static void add(Map<String, Double> counts, Map<String, String> words, String term, String surface,
                            double weight) {
        counts.merge(term, weight, Double::sum);
        words.putIfAbsent(term, surface);
    }

    private static Map<String, Integer> documentFrequency(List<? extends Set<String>> sets) {
        Map<String, Integer> df = new HashMap<>();
        for (Set<String> set : sets) {
            for (String key : set) {
                df.merge(key, 1, Integer::sum);
            }
        }
        return df;
    }

    /** 부드럽게 만든 IDF: 모든 글에 나와도 0이 되지 않아요 (별이 2~3개뿐일 때도 계산이 돼요) */
    static double idf(int n, int df) {
        return Math.log((n + 1.0) / (df + 1.0)) + 1;
    }

    /** 함께 나온 단어 중 기여가 큰 것. 한글 조각은 두 글의 원래 낱말에서 겹치는 부분("여행을"·"여행은" → "여행")으로 보여줘요 */
    private static List<String> keywords(Map<String, Double> a, Map<String, Double> b, Map<String, String> surfaceA,
                                         Map<String, String> surfaceB, List<String> tags) {
        Map<String, Double> shared = new HashMap<>();
        for (var e : a.entrySet()) {
            Double wb = b.get(e.getKey());
            if (wb != null) {
                shared.put(e.getKey(), e.getValue() * wb);
            }
        }
        Set<String> tagWords = new LinkedHashSet<>();
        tags.forEach(tag -> tagWords.add(tag.toLowerCase(Locale.ROOT)));
        Map<String, Boolean> chosen = new LinkedHashMap<>();
        shared.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .forEach(e -> {
                    if (chosen.size() >= MAX_KEYWORDS) {
                        return;
                    }
                    String word = commonPart(surfaceA.get(e.getKey()), surfaceB.get(e.getKey()), e.getKey());
                    if (!tagWords.contains(word.toLowerCase(Locale.ROOT))) {
                        chosen.putIfAbsent(word, true);
                    }
                });
        return List.copyOf(chosen.keySet());
    }

    /** 두 낱말의 가장 긴 공통 부분. 영문은 대소문자가 달라도 같은 단어라 첫 글의 표기를 써요 */
    static String commonPart(String x, String y, String term) {
        if (x == null || y == null || !isHangul(term.charAt(0))) {
            return x != null ? x : term;
        }
        int best = 0;
        int end = 0;
        int[][] len = new int[x.length() + 1][y.length() + 1];
        for (int i = 1; i <= x.length(); i++) {
            for (int j = 1; j <= y.length(); j++) {
                if (x.charAt(i - 1) == y.charAt(j - 1)) {
                    len[i][j] = len[i - 1][j - 1] + 1;
                    if (len[i][j] > best) {
                        best = len[i][j];
                        end = i;
                    }
                }
            }
        }
        return best >= 2 ? x.substring(end - best, end) : term;
    }
}
