package com.doneit.config;

import com.doneit.recurring.application.RegularTaskForm;
import com.doneit.task.application.TaskChangeHistoryService;
import com.doneit.task.web.TaskUpsertForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class TaskChangeHistoryWebConfiguration implements WebMvcConfigurer {
    private final TaskChangeHistoryService history;

    public TaskChangeHistoryWebConfiguration(TaskChangeHistoryService history) { this.history = history; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView view) {
                if (view == null) return;
                Object form = view.getModel().get("form");
                if ("task-form".equals(view.getViewName()) && form instanceof TaskUpsertForm task && task.getTaskId() != null)
                    view.addObject("changeHistory", history.get(task.getTaskId(), false));
                if ("recurring-form".equals(view.getViewName()) && form instanceof RegularTaskForm task && task.getId() != null)
                    view.addObject("changeHistory", history.get(task.getId(), true));
            }
        }).addPathPatterns("/tasks/**", "/recurring/**");
    }
}
