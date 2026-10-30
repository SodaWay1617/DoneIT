package com.doneit.task.web;

import com.doneit.project.application.ProjectService;
import com.doneit.tag.application.TagService;
import com.doneit.task.application.FinishedPageService;
import com.doneit.task.application.view.FinishedFilter;
import com.doneit.task.domain.PlanningType;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class FinishedController {
    private final FinishedPageService finished;
    private final ProjectService projects;
    private final TagService tags;
    public FinishedController(FinishedPageService finished, ProjectService projects, TagService tags) {
        this.finished = finished; this.projects = projects; this.tags = tags;
    }

    @GetMapping("/finished")
    public String page(@RequestParam(required = false) Long projectId,
                       @RequestParam(name = "tagId", required = false) List<Long> tagIds,
                       @RequestParam(defaultValue = "ALL") String status,
                       @RequestParam(defaultValue = "25") int size,
                       @RequestParam(defaultValue = "1") long page,
                       @RequestParam(required = false) PlanningType planningType, Model model) {
        var filter = new FinishedFilter(projectId, tagIds == null ? List.of() : tagIds.stream().distinct().toList(),
                status, size, planningType == null ? null : planningType.name());
        model.addAttribute("finished", finished.page(filter, page));
        model.addAttribute("projects", projects.list());
        model.addAttribute("selectedProjectId", projectId);
        model.addAttribute("finishedTags", tags.list());
        return "finished";
    }
}
