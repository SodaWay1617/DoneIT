package com.doneit.user.web;

import com.doneit.user.domain.User;
import com.doneit.user.domain.UserRepository;
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

    public SettingsController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/settings")
    public String settings(Principal principal, Model model) {
        User user = requireUser(principal);
        model.addAttribute("showProjectInTaskTitle", user.showProjectInTaskTitle());
        return "settings";
    }

    @PostMapping("/settings")
    @Transactional
    public String updateSettings(
            Principal principal,
            @RequestParam(defaultValue = "false") boolean showProjectInTaskTitle,
            RedirectAttributes redirectAttributes
    ) {
        userRepository.updateShowProjectInTaskTitle(principal.getName(), showProjectInTaskTitle);
        redirectAttributes.addFlashAttribute("flashMessage", "Settings saved.");
        return "redirect:/settings";
    }

    private User requireUser(Principal principal) {
        return userRepository.findByLogin(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));
    }
}
