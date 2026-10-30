package com.doneit.tag.web;

import com.doneit.tag.application.TagService;
import com.doneit.task.web.TaskUpsertForm;
import com.doneit.recurring.application.RegularTaskForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class TagWebConfiguration implements WebMvcConfigurer {
    private final TagService tags;
    public TagWebConfiguration(TagService tags) { this.tags=tags; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public void postHandle(HttpServletRequest request,HttpServletResponse response,Object handler,ModelAndView view) {
                if(view==null||request.getUserPrincipal()==null) return;
                String name=view.getViewName();
                if("task-form".equals(name)||"recurring-form".equals(name)) {
                    view.addObject("availableTags",tags.list());
                    if("GET".equals(request.getMethod())) {
                        Object form=view.getModel().get("form");
                        if(form instanceof TaskUpsertForm task && task.getTaskId()!=null)
                            task.setTagNames(tags.names(task.getTaskId(),false));
                        if(form instanceof RegularTaskForm task && task.getId()!=null)
                            task.setTagNames(tags.names(task.getId(),true));
                    }
                }
                if(List.of("tasks","kanban","calendar","status-list").contains(name==null?"":name)) {
                    view.addObject("taskTags",tags.visible(false));
                    view.addObject("regularTaskTags",tags.visible(true));
                }
            }
        });
    }
}
