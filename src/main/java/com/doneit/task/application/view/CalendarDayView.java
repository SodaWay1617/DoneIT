package com.doneit.task.application.view;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record CalendarDayView(
        LocalDate date,
        boolean currentMonth,
        boolean today,
        List<CalendarItemView> items,
        PlanningLoad workload,
        boolean actualWorkload
) {

    public CalendarDayView(LocalDate date, boolean currentMonth, boolean today, List<CalendarItemView> items) {
        this(date, currentMonth, today, items, PlanningLoad.empty(), false);
    }

    public CalendarDayView(LocalDate date, boolean currentMonth, boolean today, List<CalendarItemView> items, PlanningLoad workload) {
        this(date, currentMonth, today, items, workload, false);
    }

    public CalendarDayView {
        Objects.requireNonNull(date, "date is required");
        items = List.copyOf(Objects.requireNonNull(items, "items is required"));
    }
}
