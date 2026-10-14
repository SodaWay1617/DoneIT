package com.doneit.task.web;

import com.doneit.recurring.application.RegularTaskService.Occurrence;
import com.doneit.project.application.ProjectService;
import com.doneit.task.application.CalendarTrackedTimeService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import com.doneit.recurring.domain.RegularTask;
import com.doneit.task.application.view.*;
import com.doneit.task.domain.PlanningType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
public class PlanningViewInterceptor implements HandlerInterceptor {
    private static final List<String> VIEWS = List.of("tasks", "kanban", "calendar", "backlog", "status-list", "schedule");

    private final ProjectService projects;
    private final CalendarTrackedTimeService trackedTime;
    private final Clock clock;

    public PlanningViewInterceptor(ProjectService projects, CalendarTrackedTimeService trackedTime, Clock clock) {
        this.projects = projects;
        this.trackedTime = trackedTime;
        this.clock = clock;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView view) {
        if (view == null || !VIEWS.contains(view.getViewName())) return;
        String value = request.getParameter("planningType");
        PlanningType selected;
        try {
            selected = value == null || value.isBlank() ? null : PlanningType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown planning type");
        }
        if ("schedule".equals(view.getViewName())) {
            Map<String, Object> model = view.getModel();
            model.put("selectedPlanningType", selected);
            model.put("planningTypes", PlanningType.values());
            model.put("filterDate", model.get("selectedDate"));
            return;
        }
        Set<Long> personalTasks = projects.personalTaskIds(false);
        Set<Long> personalRegularTasks = projects.personalTaskIds(true);
        Map<String, Object> model = view.getModel();
        model.put("selectedPlanningType", selected);
        model.put("planningTypes", PlanningType.values());
        model.put("filterDate", request.getParameter("date"));
        model.put("filterMonth", request.getParameter("month"));
        for (String key : List.of("regularActive", "regularInbox", "regularBacklog", "regularFinished", "regularKanban")) {
            model.put(key, filtered(list(model, key, RegularTask.class), selected, RegularTask::planningType));
        }
        List<RegularTask> regularActive = list(model, "regularActive", RegularTask.class);
        for (String key : List.of("backlogPreview", "inboxPreview", "finishedPreview")) {
            if (model.containsKey(key)) model.put(key, filtered(list(model, key, TaskListItemView.class), selected, TaskListItemView::planningType));
        }
        if (model.get("daily") instanceof DailyTasksView daily) {
            DailyTasksView filtered = new DailyTasksView(daily.selectedDate(),
                    filtered(daily.activeTasks(), selected, TaskListItemView::planningType),
                    filtered(daily.completedTasks(), selected, TaskListItemView::planningType));
            model.put("daily", filtered);
            model.put("activeEntries", DailyTaskEntryView.sorted(filtered.activeTasks(), regularActive));
            model.put("planningLoad", PlanningLoad.forDay(filtered.selectedDate(), filtered.activeTasks().stream().filter(t -> personalTasks.contains(t.id())).toList(),
                    regularActive.stream().filter(t -> personalRegularTasks.contains(t.id())).toList()));
        }
        for (String key : List.of("backlog", "taskCollection")) {
            if (model.get(key) instanceof BacklogTasksView tasks) {
                model.put(key, new BacklogTasksView(filtered(tasks.tasks(), selected, TaskListItemView::planningType)));
            }
        }
        if (model.get("kanban") instanceof KanbanTasksView kanban) {
            KanbanTasksView filtered = new KanbanTasksView(kanban.selectedDate(),
                    filtered(kanban.openTasks(), selected, TaskListItemView::planningType),
                    filtered(kanban.doneTasks(), selected, TaskListItemView::planningType),
                    filtered(kanban.closedTasks(), selected, TaskListItemView::planningType));
            model.put("kanban", filtered);
            model.put("planningLoad", PlanningLoad.forDay(filtered.selectedDate(), filtered.openTasks().stream().filter(t -> personalTasks.contains(t.id())).toList(),
                    regularActive.stream().filter(t -> personalRegularTasks.contains(t.id())).toList()));
        }
        if (model.get("calendar") instanceof CalendarMonthView calendar) {
            LocalDate today = LocalDate.now(clock);
            Long projectId = (Long) model.get("selectedProjectId");
            LocalDate start = calendar.days().getFirst().date();
            LocalDate end = calendar.days().getLast().date().plusDays(1);
            Map<LocalDate, PlanningLoad> actual = start.isBefore(today)
                    ? trackedTime.forRange(start, end.isBefore(today) ? end : today, projectId, selected) : Map.of();
            List<Occurrence> occurrences = filtered(list(model, "regularCalendar", Occurrence.class),
                    selected, occurrence -> occurrence.task().planningType());
            model.put("regularCalendar", occurrences);
            List<CalendarDayView> days = calendar.days().stream().map(day -> {
                List<CalendarItemView> items = filtered(day.items(), selected, CalendarItemView::planningType);
                if (day.date().isBefore(today)) {
                    return new CalendarDayView(day.date(), day.currentMonth(), day.today(), items,
                            actual.getOrDefault(day.date(), PlanningLoad.empty()), true);
                }
                PlanningLoad load = PlanningLoad.empty();
                for (CalendarItemView item : items) {
                    if (personalTasks.contains(item.taskId()) && PlanningLoad.active(item.taskStatus()) && item.occurrenceType() == CalendarOccurrenceType.PLANNED) {
                        load = load.add(item.planningType(), item.estimateMinutes());
                    }
                }
                for (Occurrence occurrence : occurrences) {
                    if (personalRegularTasks.contains(occurrence.task().id()) && occurrence.date().equals(day.date()) && !occurrence.completed() && PlanningLoad.active(occurrence.task().status())) {
                        load = load.add(occurrence.task().planningType(), occurrence.task().estimateMinutes());
                    }
                }
                return new CalendarDayView(day.date(), day.currentMonth(), day.today(), items, load);
            }).toList();
            model.put("calendar", new CalendarMonthView(calendar.month(), calendar.previousMonthDate(), calendar.nextMonthDate(), days));
            model.put("filterMonth", calendar.monthValue());
        }
    }

    private static <T> List<T> filtered(List<T> tasks, PlanningType selected, Function<T, PlanningType> type) {
        return selected == null ? tasks : tasks.stream().filter(task -> type.apply(task) == selected).toList();
    }

    private static <T> List<T> list(Map<String, Object> model, String key, Class<T> type) {
        return model.get(key) instanceof List<?> values ? values.stream().map(type::cast).toList() : List.of();
    }
}
