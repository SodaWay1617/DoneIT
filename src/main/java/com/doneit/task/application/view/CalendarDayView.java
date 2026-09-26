package com.doneit.task.application.view;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record CalendarDayView(
        LocalDate date,
        boolean currentMonth,
        boolean today,
        List<CalendarItemView> items
) {

    public CalendarDayView {
        Objects.requireNonNull(date, "date is required");
        items = List.copyOf(Objects.requireNonNull(items, "items is required"));
    }
}
