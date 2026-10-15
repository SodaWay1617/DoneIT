package com.doneit.analytics.application;

import java.time.LocalDate;
import java.util.List;

public record PlanFactView(AnalyticsPeriod period, LocalDate start, LocalDate end, String login,
                           TimeComparison total, List<TaskRow> tasks, List<ProjectRow> projects,
                           List<DayRow> days, boolean rolledUp) {
    public LocalDate lastDate() { return end.minusDays(1); }
    public LocalDate previousDate() { return period.previous(start); }
    public LocalDate nextDate() { return period.next(start); }
    public long chartMaxMinutes() {
        return Math.max(1,days.stream().mapToLong(d->Math.max(d.comparison().plannedMinutes(),d.comparison().actualMinutes())).max().orElse(1));
    }
    public record TaskRow(Long id,boolean recurring,String title,String projectName,TimeComparison comparison) {
        public String href() { return (recurring?"/recurring/":"/tasks/")+id+"/edit"; }
    }
    public record ProjectRow(Long id,String name,TimeComparison comparison) {}
    public record DayRow(LocalDate date,TimeComparison comparison) {
        public long positiveActualMinutes() { return Math.max(0,comparison.actualMinutes()); }
    }
    public record TaskComparison(TimeComparison own,TimeComparison children) {
        public TimeComparison total() { return own.add(children); }
    }
}
