package com.doneit.config;

import com.doneit.user.application.LearningService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class LearningWebConfiguration implements WebMvcConfigurer {
    private final LearningService learning;

    public LearningWebConfiguration(LearningService learning) { this.learning = learning; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public void postHandle(HttpServletRequest request, HttpServletResponse response,
                                   Object handler, ModelAndView view) {
                if (view != null && "tasks".equals(view.getViewName()) && request.getUserPrincipal() != null)
                    view.addObject("learningProgress", learning.progress(request.getUserPrincipal().getName()));
            }
        }).addPathPatterns("/", "/tasks");
    }
}
