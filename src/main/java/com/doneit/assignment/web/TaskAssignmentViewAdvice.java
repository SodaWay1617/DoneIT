package com.doneit.assignment.web;

import com.doneit.assignment.application.TaskAssignmentService;
import java.security.Principal;
import java.util.*;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class TaskAssignmentViewAdvice {
    private final TaskAssignmentService assignments;
    public TaskAssignmentViewAdvice(TaskAssignmentService assignments) { this.assignments = assignments; }
    @ModelAttribute("taskAssignees") public Map<Long,List<String>> tasks(Principal p) { return p == null ? Map.of() : assignments.visibleTaskAssignees(); }
    @ModelAttribute("regularTaskAssignees") public Map<Long,List<String>> regular(Principal p) { return p == null ? Map.of() : assignments.visibleRegularTaskAssignees(); }
}
