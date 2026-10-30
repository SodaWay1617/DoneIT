package com.doneit.task.application;

import com.doneit.project.application.ProjectService;
import com.doneit.recurring.application.RegularTaskService;
import com.doneit.tag.application.TagService;
import com.doneit.task.application.view.TaskListItemView;
import com.doneit.task.domain.PlanningType;
import com.doneit.task.domain.TaskRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class BacklogPageService {
    private final TaskRepository tasks;
    private final RegularTaskService regular;
    private final ProjectService projects;
    private final TagService tags;
    private final Clock clock;

    public BacklogPageService(TaskRepository tasks, RegularTaskService regular, ProjectService projects,
                              TagService tags, Clock clock) {
        this.tasks = tasks;
        this.regular = regular;
        this.projects = projects;
        this.tags = tags;
        this.clock = clock;
    }

    public record Row(Long id, String title, String project, List<TagService.Tag> tags,
                      LocalDateTime createdAt, Integer estimateMinutes, boolean recurring, String returnTo) {
        public String base() { return (recurring ? "/recurring/" : "/tasks/") + id; }
        public String href() {
            return base() + "/edit?redirectTo=" + java.net.URLEncoder.encode(returnTo, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    public List<Row> list(Long projectId, PlanningType planning, String returnTo) {
        var user = projects.user();
        var names = projects.list().stream().collect(Collectors.toMap(p -> p.id(),
                p -> p.defaultProject() ? "MAIN" : p.name().split("___", 2)[0]));
        if (projectId != null && !names.containsKey(projectId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var personalTags = tags.visible(false);
        var regularTags = tags.visible(true);
        LocalDate today = LocalDate.now(clock);
        List<Row> rows = new ArrayList<>();
        tasks.findBacklogTasks(user.id()).stream()
                .filter(t -> projectId == null || projectId.equals(t.projectId()))
                .filter(t -> planning == null || planning == t.planningType())
                .forEach(t -> rows.add(new Row(t.id(), TaskListItemView.from(t, today, user.showProjectInTaskTitle()).title(),
                        names.get(t.projectId()), personalTags.getOrDefault(t.id(), List.of()), t.createdAt(),
                        t.estimateMinutes(), false, returnTo)));
        regular.backlog(projectId).stream().filter(t -> planning == null || planning == t.planningType())
                .forEach(t -> rows.add(new Row(t.id(), t.title(), names.get(t.projectId()),
                        regularTags.getOrDefault(t.id(), List.of()), t.createdAt(), t.estimateMinutes(), true, returnTo)));
        rows.sort(Comparator.comparing(Row::createdAt).reversed().thenComparing(Row::id, Comparator.reverseOrder()));
        return List.copyOf(rows);
    }
}
