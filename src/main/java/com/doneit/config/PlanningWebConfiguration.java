package com.doneit.config;

import com.doneit.task.web.PlanningViewInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PlanningWebConfiguration implements WebMvcConfigurer {
    private final PlanningViewInterceptor planning;
    public PlanningWebConfiguration(PlanningViewInterceptor planning) { this.planning = planning; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(planning); }
}
