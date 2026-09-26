package com.doneit.task.application.view;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

public record CalendarMonthView(
        YearMonth month,
        LocalDate previousMonthDate,
        LocalDate nextMonthDate,
        List<CalendarDayView> days
) {

    public CalendarMonthView {
        Objects.requireNonNull(month, "month is required");
        Objects.requireNonNull(previousMonthDate, "previousMonthDate is required");
        Objects.requireNonNull(nextMonthDate, "nextMonthDate is required");
        days = List.copyOf(Objects.requireNonNull(days, "days is required"));
    }

    public String monthValue() {
        return month.toString();
    }

    public String previousMonthValue() {
        return YearMonth.from(previousMonthDate).toString();
    }

    public String nextMonthValue() {
        return YearMonth.from(nextMonthDate).toString();
    }
}
