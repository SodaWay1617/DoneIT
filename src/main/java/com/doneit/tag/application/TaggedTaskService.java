package com.doneit.tag.application;

import com.doneit.task.application.TaskApplicationService;
import com.doneit.task.application.command.CreateTaskCommand;
import com.doneit.task.application.command.EditTaskCommand;
import com.doneit.task.application.view.TaskListItemView;
import com.doneit.recurring.application.RegularTaskService;
import com.doneit.recurring.application.RegularTaskForm;
import java.util.Collection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaggedTaskService {
    private final TaskApplicationService tasks;
    private final RegularTaskService regular;
    private final TagService tags;

    public TaggedTaskService(TaskApplicationService tasks, RegularTaskService regular, TagService tags) {
        this.tasks=tasks; this.regular=regular; this.tags=tags;
    }

    @Transactional
    public TaskListItemView create(CreateTaskCommand command, boolean backlog, Collection<String> names) {
        var normalized = TagNames.normalize(names);
        TaskListItemView task = backlog ? tasks.createBacklogTask(command) : tasks.createTask(command);
        tags.replace(task.id(),false,normalized);
        return task;
    }

    @Transactional
    public void edit(EditTaskCommand command, Collection<String> names, boolean replace) {
        if (replace) TagNames.normalize(names);
        tasks.editTask(command);
        if (replace) tags.replace(command.taskId(),false,names);
    }

    @Transactional
    public void createRegular(RegularTaskForm form) {
        var names = TagNames.normalize(form.getTagNames());
        Long id = regular.create(form);
        tags.replace(id,true,names);
    }

    @Transactional
    public void editRegular(Long id, RegularTaskForm form) {
        if (form.isTagsPresent()) TagNames.normalize(form.getTagNames());
        regular.update(id,form);
        if (form.isTagsPresent()) tags.replace(id,true,form.getTagNames());
    }
}
