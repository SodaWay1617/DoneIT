package com.doneit.user.web;

import com.doneit.user.application.WorkloadSettingsService;
import java.security.Principal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class WorkloadSettingsAdvice {
    private final WorkloadSettingsService settings;
    public WorkloadSettingsAdvice(WorkloadSettingsService settings) { this.settings = settings; }

    @ModelAttribute
    public void workloadSettings(Principal principal, Model model) {
        if (principal != null) model.addAttribute("workloadSettings", settings.current());
    }
}
