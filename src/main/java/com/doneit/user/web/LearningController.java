package com.doneit.user.web;

import com.doneit.user.application.LearningService;
import java.security.Principal;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class LearningController {
    private static final List<String> DESTINATIONS = List.of(
            "/", "/tasks/new", "/kanban", "/recurring/new", "/projects", "/schedule", "/analytics", "/settings");
    private final LearningService learning;

    public LearningController(LearningService learning) { this.learning = learning; }

    @GetMapping("/learn")
    public String resume(Principal principal) {
        return "redirect:/learn/" + learning.progress(principal.getName()).nextTopic();
    }

    @GetMapping("/learn/{topic}")
    public String topic(@PathVariable int topic, Principal principal, Model model) {
        learning.validateTopic(topic);
        model.addAttribute("topic", topic);
        model.addAttribute("progress", learning.progress(principal.getName()));
        model.addAttribute("destination", DESTINATIONS.get(topic));
        return "learn";
    }

    @PostMapping("/learn/{topic}/read")
    public String read(@PathVariable int topic, Principal principal) {
        learning.markRead(principal.getName(), topic);
        return "redirect:/learn";
    }

    @PostMapping("/learn/dismiss")
    public String dismiss(Principal principal) {
        learning.dismiss(principal.getName());
        return "redirect:/";
    }

    @PostMapping("/learn/restart")
    public String restart(Principal principal) {
        learning.restart(principal.getName());
        return "redirect:/learn/0";
    }
}
