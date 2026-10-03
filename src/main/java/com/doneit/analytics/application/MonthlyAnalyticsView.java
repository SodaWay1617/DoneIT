package com.doneit.analytics.application;

import java.time.YearMonth;
import java.util.List;

public record MonthlyAnalyticsView(YearMonth month, String monthValue, String previousMonthValue,
                                   String nextMonthValue, int totalMinutes,
                                   List<TaskTimeView> topTasks, List<ProjectTimeView> projects) {
    public String totalDuration() { return totalMinutes / 60 + "h " + totalMinutes % 60 + "m"; }
}
