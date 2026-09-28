package com.doneit.recurring.domain;

import java.time.LocalDate;

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
        if (requiresAnchorDate() && (anchorDate == null || date.isBefore(anchorDate))) {
            return false;
        }
        return switch (this) {
            case DAILY -> true;
            case WEEKDAYS -> date.getDayOfWeek().getValue() <= 5;
            case WEEKLY -> anchorDate != null && date.getDayOfWeek() == anchorDate.getDayOfWeek();
            case MONTHLY -> anchorDate != null && date.getDayOfMonth() == Math.min(
                    anchorDate.getDayOfMonth(), date.lengthOfMonth());
            case YEARLY -> anchorDate != null
                    && date.getMonth() == anchorDate.getMonth()
                    && date.getDayOfMonth() == Math.min(anchorDate.getDayOfMonth(), date.lengthOfMonth());
        };
    }
}
