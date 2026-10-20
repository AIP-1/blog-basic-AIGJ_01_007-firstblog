package com.ownlog.universe.service;

import com.ownlog.blog.domain.Blog;
import com.ownlog.blog.service.BlogService;
import com.ownlog.global.security.LoginUser;
import com.ownlog.global.util.KstTime;
import com.ownlog.universe.dto.ActivityResponse;
import com.ownlog.universe.dto.ActivityResponse.Day;
import com.ownlog.universe.repository.GraphRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 기록 잔디와 연속 기록 (AC-01 ~ AC-04). 날짜는 모두 한국 날짜예요 */
@Service
@RequiredArgsConstructor
public class ActivityService {

    static final int DAYS = 365;

    private final GraphRepository graphRepository;
    private final BlogService blogService;

    @Transactional(readOnly = true)
    public ActivityResponse getActivity(String username, LoginUser me) {
        Blog blog = blogService.getActiveBlog(username);
        LocalDate today = KstTime.today();
        LocalDate from = today.minusDays(DAYS - 1L);

        List<Day> days = graphRepository.findActivity(blog.getId(), blogService.isOwner(blog, me), KstTime.startOfDay(from))
                .stream()
                .filter(row -> !row.getDay().isAfter(today))   // 미래 날짜는 없지만 방어
                .map(row -> new Day(row.getDay(), row.getCount().intValue()))
                .toList();
        Map<LocalDate, Integer> counts = days.stream().collect(Collectors.toMap(Day::date, Day::count));

        int total = days.stream().filter(d -> d.date().getYear() == today.getYear()).mapToInt(Day::count).sum();
        return new ActivityResponse(from, today, days, currentStreak(counts, today), longestStreak(days), total);
    }

    /** 오늘(아직 안 썼으면 어제)부터 거꾸로 하루도 빠짐없이 쓴 날 수 (AC-02) */
    static int currentStreak(Map<LocalDate, Integer> counts, LocalDate today) {
        LocalDate day = counts.containsKey(today) ? today : today.minusDays(1);
        int streak = 0;
        while (counts.containsKey(day)) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }

    /** 최근 1년 안에서 가장 길게 이어 쓴 날 수 (days는 날짜 오름차순) */
    static int longestStreak(List<Day> days) {
        int longest = 0;
        int run = 0;
        LocalDate prev = null;
        for (Day d : days) {
            run = prev != null && prev.plusDays(1).equals(d.date()) ? run + 1 : 1;
            longest = Math.max(longest, run);
            prev = d.date();
        }
        return longest;
    }
}
