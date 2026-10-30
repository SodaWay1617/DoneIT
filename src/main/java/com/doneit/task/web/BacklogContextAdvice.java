package com.doneit.task.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class BacklogContextAdvice {
    @ModelAttribute
    public void context(HttpServletRequest request, Model model) {
        String target = request.getParameter("redirectTo");
        if (target != null && target.length() <= 4000 && target.matches("/finished(?:\\?[A-Za-z0-9%=&+._-]*)?"))
            model.addAttribute("finishedReturnTo", target);
        if (target != null && target.length() <= 4000 && target.matches("/inbox(?:\\?[A-Za-z0-9%=&+._-]*)?"))
            model.addAttribute("inboxReturnTo", target);
        if (target != null && target.length() <= 4000 && target.matches("/backlog(?:\\?[A-Za-z0-9%=&+._-]*)?"))
            model.addAttribute("backlogReturnTo", target);
    }
}
