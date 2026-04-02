package com.doneit.task.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("appName", "DoneIt");
        model.addAttribute("message", "Bootstrap is ready for the first MVP slice.");
        return "home";
    }
}