package com.doneit.task.application;

import com.doneit.project.application.ProjectService;
import com.doneit.tag.application.TagService;
import com.doneit.task.application.view.TaskListItemView;
import com.doneit.task.application.view.TaskListPaging;
import com.doneit.task.domain.PlanningType;
import com.doneit.task.infrastructure.persistence.TaskQueueRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BacklogPageService {
    private final TaskQueueRepository tasks;
    private final ProjectService projects;
    private final TagService tags;
    public BacklogPageService(TaskQueueRepository tasks, ProjectService projects, TagService tags) {
        this.tasks = tasks; this.projects = projects; this.tags = tags;
    }
    public record Row(Long id, String title, String project, List<TagService.Tag> tags,
                      LocalDateTime createdAt, Integer estimateMinutes, boolean recurring, String returnTo) {
        public String base() { return (recurring ? "/recurring/" : "/tasks/") + id; }
        public String href() {
            return base() + "/edit?redirectTo=" + java.net.URLEncoder.encode(returnTo, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    public record Page(List<Row> rows, TaskListPaging paging) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page page(Long projectId, PlanningType planning, int size, long number) {
        if (projectId != null && !projects.canAccess(projectId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var user = projects.user();
        var page = tasks.page(user.id(), true, projectId, planning == null ? null : planning.name(), size, number);
        var personalTags = tags.visible(false, page.rows().stream().filter(t -> !t.recurring()).map(TaskQueueRepository.Entry::id).toList());
        var regularTags = tags.visible(true, page.rows().stream().filter(TaskQueueRepository.Entry::recurring).map(TaskQueueRepository.Entry::id).toList());
        String returnTo = page.paging().href(page.paging().number());
        return new Page(page.rows().stream().map(t -> new Row(t.id(),
                t.recurring() ? t.title() : TaskListItemView.displayTitle(t.taskKey(), t.title(), user.showProjectInTaskTitle()),
                t.project(), (t.recurring() ? regularTags : personalTags).getOrDefault(t.id(), List.of()),
                t.createdAt(), t.estimateMinutes(), t.recurring(), returnTo)).toList(), page.paging());
    }
}
