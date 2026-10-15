package com.doneit.analytics.application;

import java.time.LocalDate;
import java.util.List;

public record WorkloadAnalyticsView(LocalDate start,LocalDate end,int rangeDays,LocalDate historySince,
        long plannedMinutes,long actualMinutes,long reschedules,long rescheduledTasks,long cancelled,
        PlanFactService.Accuracy accuracy,List<Day> days,List<Project> projects) {
    public long overloadedDays() { return days.stream().filter(Day::overloaded).count(); }
    public boolean partialHistory() { return start.isBefore(historySince); }
    public record Day(LocalDate date,long requiredMinutes,long wantedMinutes,long optionalMinutes,
                      long actualMinutes,boolean planKnown,String level,boolean overloaded) {
        public long plannedMinutes() { return requiredMinutes+wantedMinutes+optionalMinutes; }
    }
    public record Project(Long id,String name,long minutes) {}
}
