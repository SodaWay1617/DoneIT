package com.doneit.task.application.view;

import java.time.LocalDate;
import java.util.List;

public record CalendarOverview(String mode, String monthTitle, String monthValue, LocalDate selectedWeek,
        String previousHref, String nextHref, String todayHref, String monthHref, String weekHref,
        String returnTo, List<Week> weeks) {
    public record Week(LocalDate start, int number, String href, List<Day> days) {}
    public record Day(CalendarDayView source, int minutes, int count, String level, int fill,
            String href, String weekHref, List<Important> important) {
        public boolean hasDeadline() { return important.stream().anyMatch(Important::deadline); }
        public boolean hasCritical() { return important.stream().anyMatch(Important::critical); }
    }
    public record Important(String key, String title, boolean deadline, boolean critical, String href) {}
}
