package com.doneit.assignment.web;

import com.doneit.assignment.application.TaskAssignmentService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TaskAssignmentController {
    private final TaskAssignmentService assignments;
    public TaskAssignmentController(TaskAssignmentService assignments) { this.assignments = assignments; }
    @PostMapping("/tasks/{id}/assignees")
    public String task(@PathVariable Long id, @RequestParam(required = false) List<Long> userIds, RedirectAttributes flash) {
        assignments.assignTask(id, userIds); flash.addFlashAttribute("flashMessage", "Assignees updated.");
        return "redirect:/tasks/" + id + "/edit";
    }
    @PostMapping("/recurring/{id}/assignees")
    public String regular(@PathVariable Long id, @RequestParam(required = false) List<Long> userIds, RedirectAttributes flash) {
        assignments.assignRegularTask(id, userIds); flash.addFlashAttribute("flashMessage", "Assignees updated.");
        return "redirect:/recurring/" + id + "/edit";
    }
}
