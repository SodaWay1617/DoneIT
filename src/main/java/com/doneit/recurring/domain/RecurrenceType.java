package com.doneit.recurring.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public enum RecurrenceType {
    DAILY("Every day"),
    WEEKDAYS("Every weekday"),
    WEEKLY("Every week"),
    MONTHLY("Every month"),
    YEARLY("Every year");

    private final String displayName;

    RecurrenceType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean requiresAnchorDate() {
        return this == WEEKLY || this == MONTHLY || this == YEARLY;
    }

    public boolean occursOn(LocalDate date, LocalDate anchorDate) {
        return occursOn(date, anchorDate, 1);
    }

    public boolean occursOn(LocalDate date, LocalDate anchorDate, int repeatInterval) {
        if (repeatInterval < 1) {
            throw new IllegalArgumentException("Repeat interval must be positive");
        }
        if ((requiresAnchorDate() && anchorDate == null)
                || (anchorDate != null && date.isBefore(anchorDate))) {
            return false;
        }
        return switch (this) {
            case DAILY -> anchorDate == null || ChronoUnit.DAYS.between(anchorDate, date) % repeatInterval == 0;
            case WEEKDAYS -> date.getDayOfWeek().getValue() <= 5;
            case WEEKLY -> anchorDate != null
                    && date.getDayOfWeek() == anchorDate.getDayOfWeek()
                    && ChronoUnit.WEEKS.between(anchorDate, date) % repeatInterval == 0;
            case MONTHLY -> anchorDate != null && date.getDayOfMonth() == Math.min(
                    anchorDate.getDayOfMonth(), date.lengthOfMonth())
                    && ChronoUnit.MONTHS.between(anchorDate.withDayOfMonth(1), date.withDayOfMonth(1)) % repeatInterval == 0;
            case YEARLY -> anchorDate != null
                    && date.getMonth() == anchorDate.getMonth()
                    && date.getDayOfMonth() == Math.min(anchorDate.getDayOfMonth(), date.lengthOfMonth())
                    && ChronoUnit.YEARS.between(anchorDate.withDayOfYear(1), date.withDayOfYear(1)) % repeatInterval == 0;
        };
    }
}
