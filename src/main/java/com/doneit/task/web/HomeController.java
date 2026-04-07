package com.doneit.task.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Model model, Principal principal) {
        model.addAttribute("appName", "DoneIt");
        model.addAttribute("message", "Bootstrap is ready for the first MVP slice.");
        model.addAttribute("username", principal == null ? "unknown" : principal.getName());
        return "home";
    }
}