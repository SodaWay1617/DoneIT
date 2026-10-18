package com.doneit.user.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(jakarta.servlet.http.HttpServletRequest request,org.springframework.ui.Model model) {
        model.addAttribute("secureConnection",request.isSecure());
        return "login";
    }
}
