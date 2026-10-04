package com.doneit.user.web;

import com.doneit.user.domain.User;
import com.doneit.user.domain.UserRepository;
import com.doneit.user.application.WorkloadSettingsService;
import com.doneit.user.application.AnalyticsSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SettingsController {

    private final UserRepository userRepository;
    private final WorkloadSettingsService workloadSettings;
    private final AnalyticsSettingsService analyticsSettings;

    public SettingsController(UserRepository userRepository, WorkloadSettingsService workloadSettings) {
        this(userRepository, workloadSettings, null);
    }

    @Autowired
    public SettingsController(UserRepository userRepository, WorkloadSettingsService workloadSettings,
                              AnalyticsSettingsService analyticsSettings) {
        this.userRepository = userRepository;
        this.workloadSettings = workloadSettings;
        this.analyticsSettings = analyticsSettings;
    }

    @GetMapping("/settings")
    public String settings(Principal principal, Model model) {
        User user = requireUser(principal);
        model.addAttribute("showProjectInTaskTitle", user.showProjectInTaskTitle());
        model.addAttribute("workload", workloadSettings.current());
        model.addAttribute("rollUpSubtaskTime", analyticsSettings != null && analyticsSettings.rollUpSubtaskTime());
        return "settings";
    }

    @PostMapping("/settings")
    @Transactional
    public String updateSettings(
            Principal principal,
            @RequestParam(defaultValue = "false") boolean showProjectInTaskTitle,
            @RequestParam(defaultValue = "false") boolean rollUpSubtaskTime,
            @RequestParam int workloadGreenHours,
            @RequestParam int workloadYellowHours,
            @RequestParam int workloadOrangeHours,
            RedirectAttributes redirectAttributes
    ) {
        userRepository.updateShowProjectInTaskTitle(principal.getName(), showProjectInTaskTitle);
        workloadSettings.update(workloadGreenHours, workloadYellowHours, workloadOrangeHours);
        if (analyticsSettings != null) analyticsSettings.update(rollUpSubtaskTime);
        redirectAttributes.addFlashAttribute("settingsSaved", true);
        return "redirect:/settings";
    }

    private User requireUser(Principal principal) {
        return userRepository.findByLogin(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));
    }
}
