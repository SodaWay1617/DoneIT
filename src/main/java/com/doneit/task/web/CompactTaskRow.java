package com.doneit.task.web;

import com.doneit.tag.application.TagService.Tag;
import com.doneit.task.domain.PlanningType;
import com.doneit.task.domain.TaskPriority;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;

public record CompactTaskRow(Long id, boolean recurring, String title, String projectName,
        TaskPriority priority, PlanningType planningType, boolean overdue, LocalDate plannedDate,
        LocalTime start, Integer estimateMinutes, String interval, long nextDays,
        List<Tag> tags, List<String> assignees) {
    public String href() { return (recurring ? "/recurring/" : "/tasks/") + id + "/edit"; }
    public String base() { return (recurring ? "/recurring/" : "/tasks/") + id; }
    public String plannedAt() { return start == null || plannedDate == null ? "" : plannedDate + "T" + start; }

    public static String interval(LocalTime start, LocalTime end, Integer estimate) {
        if (start == null) return "";
        var format=DateTimeFormatter.ofPattern("HH:mm");
        Long duration=duration(start,end,estimate);
        return start.format(format)+(duration==null ? "" : "–"+start.plusMinutes(duration).format(format));
    }
    public static long nextDays(LocalTime start, LocalTime end, Integer estimate) {
        Long duration=duration(start,end,estimate);
        return start==null||duration==null ? 0 : (start.toSecondOfDay()/60+duration)/1440;
    }
    private static Long duration(LocalTime start,LocalTime end,Integer estimate) {
        if(start==null) return null;
        if(end!=null) {
            long minutes=Duration.between(start,end).toMinutes();
            return minutes<=0 ? minutes+1440 : minutes;
        }
        return estimate==null ? null : estimate.longValue();
    }
}
