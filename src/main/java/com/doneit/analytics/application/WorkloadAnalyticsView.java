package com.doneit.analytics.application;

import java.time.LocalDate;
import java.util.List;
import com.doneit.user.application.WorkloadSettings;

public record WorkloadAnalyticsView(LocalDate start,LocalDate end,int rangeDays,LocalDate historySince,
        long plannedMinutes,long actualMinutes,long reschedules,long rescheduledTasks,long cancelled,
        PlanFactService.Accuracy accuracy,List<Day> days,List<Project> projects,WorkloadSettings thresholds,List<Task> tasks) {
    public long overloadedDays() { return days.stream().filter(Day::overloaded).count(); }
    public boolean partialHistory() { return start.isBefore(historySince); }
    public Averages averages() {
        List<Day> known=days.stream().filter(Day::planKnown).toList();
        int count=known.size();
        long required=known.stream().mapToLong(Day::requiredMinutes).sum();
        long wanted=known.stream().mapToLong(Day::wantedMinutes).sum();
        long optional=known.stream().mapToLong(Day::optionalMinutes).sum();
        return new Averages(count,List.of(scale("planning.REQUIRED",required,count),
                scale("planning.WANTED",wanted,count),scale("planning.OPTIONAL",optional,count),
                scale("workload.average.total",required+wanted+optional,count)),
                Math.min(1440,thresholds.greenMinutes())/14.4,
                Math.min(1440,thresholds.yellowMinutes())/14.4,
                Math.min(1440,thresholds.orangeMinutes())/14.4);
    }
    private Scale scale(String label,long total,int count) {
        long minutes=count==0?0:Math.round(total/(double)count);
        return new Scale(label,minutes,Math.min(1440,minutes)/14.4,
                thresholds.level((int)Math.min(Integer.MAX_VALUE,minutes)));
    }
    public record Averages(int sampleDays,List<Scale> scales,double greenEnd,double yellowEnd,double orangeEnd) {
        public boolean available() { return sampleDays>0; }
    }
    public record Scale(String labelKey,long minutes,double position,String level) {
        public long meterMinutes() { return Math.min(1440,minutes); }
        public boolean overflow() { return minutes>1440; }
    }
    public record Day(LocalDate date,long requiredMinutes,long wantedMinutes,long optionalMinutes,
                      long actualMinutes,boolean planKnown,String level,boolean overloaded) {
        public long plannedMinutes() { return requiredMinutes+wantedMinutes+optionalMinutes; }
    }
    public record Project(Long id,String name,long minutes) {}
    public record Task(Long id,boolean recurring,String title,String projectName,
            com.doneit.task.domain.TaskStatus status,long plannedMinutes,long actualMinutes,boolean planned,boolean tracked) {
        public String href() { return (recurring?"/recurring/":"/tasks/")+id+"/edit"; }
    }
}
