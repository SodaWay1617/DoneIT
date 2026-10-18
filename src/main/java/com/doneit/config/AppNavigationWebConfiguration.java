package com.doneit.config;

import com.doneit.navigation.AppNavigationService;
import com.doneit.project.application.ProjectService;
import com.doneit.project.domain.Project;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AppNavigationWebConfiguration implements WebMvcConfigurer {
    private static final List<String> VIEWS = List.of("tasks", "kanban", "calendar", "schedule", "backlog", "status-list",
            "task-form", "recurring-form", "projects", "project-detail", "settings", "devices", "analytics", "workload-analytics", "reports", "learn");
    private final AppNavigationService navigation;
    private final ProjectService projects;

    public AppNavigationWebConfiguration(AppNavigationService navigation, ProjectService projects) {
        this.navigation = navigation;
        this.projects = projects;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView view) {
                if (view == null || !VIEWS.contains(view.getViewName()) || request.getUserPrincipal() == null) return;
                Object existing = view.getModel().get("projects");
                List<Project> available = existing instanceof List<?> list
                        ? list.stream().filter(Project.class::isInstance).map(Project.class::cast).toList() : projects.list();
                Long selected = view.getModel().get("selectedProjectId") instanceof Long id ? id : null;
                if ("project-detail".equals(view.getViewName()) && view.getModel().get("project") instanceof Project project)
                    selected = project.id();
                view.addObject("appNav", navigation.build(request, available, selected));
            }
        });
    }
}
