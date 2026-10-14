package com.doneit.task.domain;

import java.time.LocalDateTime;
import java.time.LocalTime;

public final class TaskInterval {
    private TaskInterval() {}
    public static void validate(LocalTime start, LocalTime end) {
        if (end != null && (start == null || end.equals(start)))
            throw new IllegalArgumentException("An explicit end requires a start and must differ from it");
    }
    public static LocalDateTime endAt(LocalDateTime start, LocalTime end, Integer estimate) {
        if (start == null) return null;
        if (end == null) return estimate == null ? null : start.plusMinutes(estimate);
        LocalDateTime result = start.toLocalDate().atTime(end);
        return result.isAfter(start) ? result : result.plusDays(1);
    }
    public static int minutes(LocalTime start, LocalTime end) {
        validate(start,end);
        int result = (int) java.time.Duration.between(start,end).toMinutes();
        return result > 0 ? result : result + 1440;
    }
}
