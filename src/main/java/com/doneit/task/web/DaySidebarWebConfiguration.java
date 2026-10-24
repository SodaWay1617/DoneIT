package com.doneit.task.web;

import com.doneit.task.application.TaskApplicationService;
import com.doneit.task.application.view.DailyTasksView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.*;
import java.time.format.*;
import java.util.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@Configuration
public class DaySidebarWebConfiguration implements WebMvcConfigurer {
    private final TaskApplicationService tasks;
    private final Clock clock;
    public DaySidebarWebConfiguration(TaskApplicationService tasks,Clock clock) { this.tasks=tasks;this.clock=clock; }
    public record Day(LocalDate date,boolean currentMonth,boolean today,boolean selected,String label,String href) {}
    public record Month(String title,List<String> weekdays,List<Day> days,String previousHref,String nextHref) {}
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public void postHandle(HttpServletRequest request,HttpServletResponse response,Object handler,ModelAndView view) {
                if(view!=null&&"tasks".equals(view.getViewName())&&request.getUserPrincipal()!=null) build(request,view);
            }
        }).order(-200);
    }
    @SuppressWarnings("unchecked")
    private void build(HttpServletRequest request,ModelAndView view) {
        var model=view.getModel();
        var daily=(DailyTasksView)model.get("daily");
        LocalDate selected=daily.selectedDate();
        YearMonth month;
        try {
            String value=request.getParameter("navMonth");
            month=value==null||value.isBlank()?YearMonth.from(selected):YearMonth.parse(value);
            if(month.getYear()<1||month.getYear()>9998) throw new DateTimeException("Month out of range");
        } catch(DateTimeException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid navigation month"); }
        Long project=(Long)model.get("selectedProjectId");
        // Reuse the full calendar's Monday-first grid and leap-year/year-boundary handling.
        var calendar=tasks.getCalendarMonth(month,project);
        Locale locale=LocaleContextHolder.getLocale();
        String title=month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy",locale));
        title=Character.toUpperCase(title.charAt(0))+title.substring(1);
        String current=(String)model.get("compactReturnTo");
        var context=UriComponentsBuilder.fromUriString(current);
        List<Day> days=new ArrayList<>();
        for(var day:calendar.days()) {
            var uri=UriComponentsBuilder.fromUriString(current).replacePath("/tasks")
                    .replaceQueryParam("date",day.date()).replaceQueryParam("navMonth",YearMonth.from(day.date()));
            days.add(new Day(day.date(),day.currentMonth(),day.today(),day.date().equals(selected),
                    day.date().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)),uri.build().toUriString()));
        }
        model.put("miniCalendar",new Month(title,
                Arrays.stream(DayOfWeek.values()).map(day->day.getDisplayName(TextStyle.SHORT_STANDALONE,locale)).toList(),
                List.copyOf(days),
                month.getYear()==1&&month.getMonthValue()==1?null:context.cloneBuilder().replaceQueryParam("navMonth",month.minusMonths(1)).build().toUriString(),
                month.getYear()==9998&&month.getMonthValue()==12?null:context.cloneBuilder().replaceQueryParam("navMonth",month.plusMonths(1)).build().toUriString()));
        List<CompactTaskRow> rows=(List<CompactTaskRow>)model.get("compactRows");
        model.put("daySchedule",rows.stream().filter(row->row.start()!=null&&(row.recurring()||selected.equals(row.plannedDate())))
                .sorted(Comparator.comparing(CompactTaskRow::start).thenComparing(CompactTaskRow::title,String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(CompactTaskRow::recurring).thenComparing(CompactTaskRow::id)).toList());
        model.put("dayBulkPreview",tasks.previewBulkMoves(selected));
        model.put("dayRealToday",LocalDate.now(clock));
        if(request.getParameter("navMonth")!=null) {
            model.put("compactReturnTo",context.replaceQueryParam("navMonth",month).build().toUriString());
            model.put("compactResetTo",UriComponentsBuilder.fromUriString((String)model.get("compactResetTo"))
                    .replaceQueryParam("navMonth",month).build().toUriString());
        }
    }
}
