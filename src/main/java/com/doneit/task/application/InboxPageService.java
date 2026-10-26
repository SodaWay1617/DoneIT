package com.doneit.task.application;

import com.doneit.project.application.ProjectService;
import com.doneit.tag.application.TagService;
import com.doneit.task.application.view.TaskListItemView;
import com.doneit.task.domain.TaskRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InboxPageService {
    private final TaskRepository tasks;
    private final ProjectService projects;
    private final TagService tags;
    private final Clock clock;

    public InboxPageService(TaskRepository tasks, ProjectService projects, TagService tags, Clock clock) {
        this.tasks = tasks;
        this.projects = projects;
        this.tags = tags;
        this.clock = clock;
    }

    public record Row(Long id, String title, String project, List<TagService.Tag> tags, LocalDateTime createdAt) {
        public String base() { return "/tasks/" + id; }
        public String href() { return base() + "/edit"; }
    }

    public List<Row> list(Long projectId) {
        var user = projects.user();
        Map<Long, String> names = projects.list().stream().collect(Collectors.toMap(p -> p.id(),
                p -> p.defaultProject() ? "MAIN" : p.name().split("___", 2)[0]));
        if (projectId != null && !names.containsKey(projectId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var personalTags = tags.visible(false);
        LocalDate today = LocalDate.now(clock);
        return tasks.findInboxTasks(user.id()).stream()
                .filter(t -> projectId == null || projectId.equals(t.projectId()))
                .map(t -> new Row(t.id(), TaskListItemView.from(t, today, user.showProjectInTaskTitle()).title(),
                        names.get(t.projectId()), personalTags.getOrDefault(t.id(), List.of()), t.createdAt()))
                .toList();
    }
}
