package com.doneit.comment.web;

import com.doneit.comment.application.TaskCommentService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TaskCommentController {
    private final TaskCommentService comments;
    public TaskCommentController(TaskCommentService comments) { this.comments = comments; }

    @PostMapping("/tasks/{id}/comments")
    public String addTaskComment(@PathVariable Long id, @RequestParam String body,
                                 RedirectAttributes flash) {
        comments.addToTask(id, body);
        flash.addFlashAttribute("flashMessage", "Comment added.");
        return "redirect:/tasks/" + id + "/edit";
    }

    @PostMapping("/recurring/{id}/comments")
    public String addRegularTaskComment(@PathVariable Long id, @RequestParam String body,
                                        RedirectAttributes flash) {
        comments.addToRegularTask(id, body);
        flash.addFlashAttribute("flashMessage", "Comment added.");
        return "redirect:/recurring/" + id + "/edit";
    }
}
