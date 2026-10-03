package com.doneit.comment.web;

import com.doneit.comment.application.TaskCommentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class TaskCommentViewAdvice {
    private final TaskCommentService comments;
    public TaskCommentViewAdvice(TaskCommentService comments) { this.comments = comments; }

    @ModelAttribute
    public void comments(HttpServletRequest request, Model model) {
        String[] parts = request.getRequestURI().split("/");
        if (parts.length != 4 || !"edit".equals(parts[3])) return;
        try {
            Long id = Long.valueOf(parts[2]);
            if ("tasks".equals(parts[1])) model.addAttribute("comments", comments.forTask(id));
            if ("recurring".equals(parts[1])) model.addAttribute("comments", comments.forRegularTask(id));
        } catch (NumberFormatException ignored) {
            // Not a task edit route.
        }
    }
}
