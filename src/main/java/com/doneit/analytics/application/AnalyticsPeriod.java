package com.doneit.analytics.application;

import java.time.LocalDate;

public enum AnalyticsPeriod {
    DAY, WEEK, MONTH;
    public LocalDate start(LocalDate date) {
        return switch(this) {
            case DAY -> date;
            case WEEK -> date.minusDays(date.getDayOfWeek().getValue()-1);
            case MONTH -> date.withDayOfMonth(1);
        };
    }
    public LocalDate next(LocalDate start) {
        return switch(this) { case DAY -> start.plusDays(1); case WEEK -> start.plusWeeks(1); case MONTH -> start.plusMonths(1); };
    }
    public LocalDate previous(LocalDate start) {
        return switch(this) { case DAY -> start.minusDays(1); case WEEK -> start.minusWeeks(1); case MONTH -> start.minusMonths(1); };
    }
}
