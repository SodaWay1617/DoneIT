package com.doneit.task.web;

import com.doneit.recurring.application.RegularTaskService.Occurrence;
import com.doneit.task.application.CalendarOverviewService;
import com.doneit.task.application.CalendarWeekService;
import com.doneit.project.domain.Project;
import com.doneit.task.application.view.CalendarMonthView;
import com.doneit.task.domain.PlanningType;
import com.doneit.user.application.WorkloadSettings;
import jakarta.servlet.http.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class CalendarOverviewWebConfiguration implements WebMvcConfigurer {
    private final CalendarOverviewService overview;
    private final CalendarWeekService weeks;
    public CalendarOverviewWebConfiguration(CalendarOverviewService overview,CalendarWeekService weeks) { this.overview=overview;this.weeks=weeks; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override @SuppressWarnings("unchecked")
            public void postHandle(HttpServletRequest request,HttpServletResponse response,Object handler,ModelAndView view) {
                if(view==null||!"calendar".equals(view.getViewName()))return;
                var model=view.getModel();
                var result=overview.build((CalendarMonthView)model.get("calendar"),
                        (List<Occurrence>)model.getOrDefault("regularCalendar",List.of()),
                        (WorkloadSettings)model.get("workloadSettings"),(Long)model.get("selectedProjectId"),
                        (PlanningType)model.get("selectedPlanningType"),(String)model.get("calendarMode"),
                        (LocalDate)model.get("calendarWeek"),(LocalDate)model.get("calendarAnchor"),(LocalDate)model.get("calendarFocus"),org.springframework.context.i18n.LocaleContextHolder.getLocale());
                view.addObject("overview",result);
                if("week".equals(model.get("calendarMode")))
                    view.addObject("weekView",weeks.build((CalendarMonthView)model.get("calendar"),result,
                            (List<Occurrence>)model.getOrDefault("regularCalendar",List.of()),(List<Project>)model.get("projects"),
                            (LocalDate)model.get("calendarAnchor"),(LocalDate)model.get("calendarFocus")));
            }
        }).order(-200);
    }
}
