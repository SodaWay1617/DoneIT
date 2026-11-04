package com.doneit.task.web;

import com.doneit.recurring.application.RegularTaskService.Occurrence;
import com.doneit.task.application.CalendarOverviewService;
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
    public CalendarOverviewWebConfiguration(CalendarOverviewService overview) { this.overview=overview; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override @SuppressWarnings("unchecked")
            public void postHandle(HttpServletRequest request,HttpServletResponse response,Object handler,ModelAndView view) {
                if(view==null||!"calendar".equals(view.getViewName()))return;
                var model=view.getModel();
                view.addObject("overview",overview.build((CalendarMonthView)model.get("calendar"),
                        (List<Occurrence>)model.getOrDefault("regularCalendar",List.of()),
                        (WorkloadSettings)model.get("workloadSettings"),(Long)model.get("selectedProjectId"),
                        (PlanningType)model.get("selectedPlanningType"),(String)model.get("calendarMode"),
                        (LocalDate)model.get("calendarWeek"),org.springframework.context.i18n.LocaleContextHolder.getLocale()));
            }
        }).order(-200);
    }
}
