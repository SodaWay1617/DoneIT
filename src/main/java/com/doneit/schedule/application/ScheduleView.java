package com.doneit.schedule.application;

import com.doneit.task.domain.PlanningType;
import com.doneit.task.domain.TaskStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ScheduleView(LocalDate date, List<Column> columns, List<Entry> unscheduled) {
    public record Person(Long id, String login) {}
    public record Entry(Long id, boolean recurring, String title, LocalDateTime start, LocalDateTime end,
                        PlanningType planningType, TaskStatus status, List<Person> people, boolean editable) {
        public Entry(Long id, boolean recurring, String title, LocalDateTime start, LocalDateTime end,
                     PlanningType planningType, TaskStatus status, List<Person> people) {
            this(id, recurring, title, start, end, planningType, status, people, true);
        }
        public String href() { return editable ? (recurring ? "/recurring/" : "/tasks/") + id + "/edit" : null; }
        public String timeLabel() {
            if (start == null) return "";
            return start.toLocalTime().toString() + (end == null ? "" : " - " + end.toLocalTime()
                    + (end.toLocalDate().isAfter(start.toLocalDate()) ? " (+" + java.time.temporal.ChronoUnit.DAYS.between(start.toLocalDate(),end.toLocalDate()) + ")" : ""));
        }
        public String peopleLabel() { return String.join(", ", people.stream().map(Person::login).toList()); }
    }
    public record Block(Entry entry, int lane, int topMinutes, int durationMinutes) {
        public int topPixels() { return topMinutes * 2; }
        public int heightPixels() { return durationMinutes * 2; }
        public boolean compact() { return durationMinutes < 15; }
    }
    public record Column(Person person, boolean mine, int lanes, List<Block> blocks) {
        public int widthPixels() { return lanes * 224; }
    }
}
