package com.doneit.task.application.view;

import com.doneit.task.domain.PlanningType;
import com.doneit.task.domain.TaskPriority;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record CalendarWeekView(LocalDate start,LocalDate end,LocalDate today,LocalDate focus,
        LocalDate anchor,List<Day> days) {
    public record Day(CalendarOverview.Day summary,List<Card> cards) {}
    public record Card(String key,String title,String project,LocalTime time,LocalTime deadlineTime,
            TaskPriority priority,PlanningType planningType,String state,boolean deadline,
            boolean recurring,String href) {
        public boolean critical() { return priority==TaskPriority.CRITICAL; }
    }
}
