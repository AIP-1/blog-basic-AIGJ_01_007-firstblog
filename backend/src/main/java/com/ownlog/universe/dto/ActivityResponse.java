package com.ownlog.universe.dto;

import java.time.LocalDate;
import java.util.List;

/** API 14.2 기록 잔디 (AC-01, AC-02). days에는 글을 쓴 날만 있어요 */
public record ActivityResponse(LocalDate from, LocalDate to, List<Day> days,
                               int currentStreak, int longestStreak, int totalThisYear) {

    public record Day(LocalDate date, int count) {
    }
}
