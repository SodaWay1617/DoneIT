package com.doneit.task.application;

import com.doneit.recurring.application.RegularTaskService.Occurrence;
import com.doneit.task.application.view.*;
import com.doneit.task.application.view.CalendarOverview.*;
import com.doneit.task.domain.PlanningType;
import com.doneit.task.domain.TaskPriority;
import com.doneit.user.application.WorkloadSettings;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class CalendarOverviewService {
    private final Clock clock;
    public CalendarOverviewService(Clock clock) { this.clock=clock; }

    public CalendarOverview build(CalendarMonthView calendar, List<Occurrence> occurrences,
            WorkloadSettings settings, Long project, PlanningType planning, String mode,
            LocalDate selectedWeek, Locale locale) {
        LocalDate today=LocalDate.now(clock);
        LocalDate week=(selectedWeek!=null?selectedWeek:
                YearMonth.from(today).equals(calendar.month())?today:calendar.month().atDay(1))
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        String back=link(calendar.month(),mode,week,project,planning);
        Map<LocalDate,List<Occurrence>> regular=occurrences.stream().collect(Collectors.groupingBy(Occurrence::date));
        List<Week> weeks=new ArrayList<>();
        for(int offset=0;offset<calendar.days().size();offset+=7) {
            LocalDate start=calendar.days().get(offset).date();
            String weekHref=link(calendar.month(),"week",start,project,planning)+"#legacy-day-"+start;
            List<Day> days=new ArrayList<>();
            for(CalendarDayView day:calendar.days().subList(offset,offset+7)) {
                Map<String,Important> important=new LinkedHashMap<>();
                Set<String> tasks=new HashSet<>();
                for(CalendarItemView item:day.items()) {
                    String key="task-"+item.taskId();tasks.add(key);
                    boolean critical=item.priority()==TaskPriority.CRITICAL;
                    if(item.deadlineHighlighted()||critical) {
                        Important previous=important.get(key);
                        important.put(key,new Important(key,item.title(),item.deadlineHighlighted()||previous!=null&&previous.deadline(),critical,
                                editor("/tasks/"+item.taskId()+"/edit",back)));
                    }
                }
                for(Occurrence occurrence:regular.getOrDefault(day.date(),List.of())) {
                    var task=occurrence.task();String key="regular-"+task.id();tasks.add(key);
                    if(task.priority()==TaskPriority.CRITICAL)
                        important.put(key,new Important(key,task.title(),false,true,editor("/recurring/"+task.id()+"/edit",back)));
                }
                int minutes=day.workload().totalMinutes();
                int fill=(int)Math.min(100,Math.max(0,Math.round(minutes*100.0/settings.orangeMinutes())));
                var events=important.values().stream().sorted(Comparator
                        .comparingInt((Important item)->item.deadline()?(item.critical()?0:1):2)
                        .thenComparing(Important::title,String.CASE_INSENSITIVE_ORDER).thenComparing(Important::key)).toList();
                var dayLink=UriComponentsBuilder.fromPath("/tasks").queryParam("date",day.date());
                if(project!=null)dayLink.queryParam("projectId",project);
                if(planning!=null)dayLink.queryParam("planningType",planning);
                days.add(new Day(day,minutes,tasks.size(),settings.level(minutes),fill,dayLink.build().toUriString(),weekHref,events));
            }
            weeks.add(new Week(start,start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),weekHref,List.copyOf(days)));
        }
        String title=calendar.month().atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy",locale));
        title=title.substring(0,1).toUpperCase(locale)+title.substring(1);
        return new CalendarOverview(mode,title,calendar.monthValue(),week,
                link(calendar.month().minusMonths(1),mode,null,project,planning),
                link(calendar.month().plusMonths(1),mode,null,project,planning),
                link(YearMonth.from(today),mode,today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),project,planning),
                link(calendar.month(),"month",week,project,planning),link(calendar.month(),"week",week,project,planning),back,List.copyOf(weeks));
    }

    private static String editor(String path,String back) {
        return UriComponentsBuilder.fromPath(path).queryParam("redirectTo",back).build().encode().toUriString();
    }
    private static String link(YearMonth month,String mode,LocalDate week,Long project,PlanningType planning) {
        var uri=UriComponentsBuilder.fromPath("/calendar").queryParam("month",month).queryParam("mode",mode);
        if("week".equals(mode)&&week!=null)uri.queryParam("week",week);
        if(project!=null)uri.queryParam("projectId",project);
        if(planning!=null)uri.queryParam("planningType",planning);
        return uri.build().toUriString();
    }
}
