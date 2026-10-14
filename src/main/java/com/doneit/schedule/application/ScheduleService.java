package com.doneit.schedule.application;

import com.doneit.assignment.application.TaskAssignmentService;
import com.doneit.project.application.ProjectService;
import com.doneit.project.application.TaskVisibility;
import com.doneit.recurring.application.RegularTaskService;
import com.doneit.schedule.application.ScheduleView.*;
import com.doneit.task.domain.*;
import com.doneit.task.infrastructure.persistence.TaskRowMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ScheduleService {
    private final JdbcTemplate db;
    private final ProjectService projects;
    private final TaskAssignmentService assignments;
    private final RegularTaskService recurring;
    private final TaskRowMapper mapper;

    public ScheduleService(JdbcTemplate db, ProjectService projects, TaskAssignmentService assignments,
                           RegularTaskService recurring, TaskRowMapper mapper) {
        this.db=db; this.projects=projects; this.assignments=assignments; this.recurring=recurring; this.mapper=mapper;
    }

    public ScheduleView day(LocalDate date, Long projectId, PlanningType category) {
        if (projectId != null) projects.get(projectId);
        Long viewer = projects.user().id();
        List<Person> people = db.query("""
                SELECT DISTINCT u.id,u.login FROM project_members m JOIN users u ON u.id=m.user_id
                JOIN project_members viewer ON viewer.project_id=m.project_id AND viewer.user_id=?
                WHERE CAST(? AS bigint) IS NULL OR m.project_id=?
                """, (r,n)->new Person(r.getLong("id"),r.getString("login")),viewer,projectId,projectId);
        Map<String,Person> byLogin = new HashMap<>();
        people.forEach(p -> byLogin.put(p.login(),p));
        Map<Long,List<String>> ordinaryAssignments = assignments.visibleTaskAssignees();
        Map<Long,List<String>> regularAssignments = assignments.visibleRegularTaskAssignees();
        LocalDateTime start = date.atStartOfDay(), end = start.plusDays(1);
        List<Task> tasks = db.query("""
                SELECT t.* FROM tasks t WHERE %s
                  AND (CAST(? AS bigint) IS NULL OR t.project_id=?)
                  AND t.status IN ('SPECIFICATION','TODO','IN_PROGRESS','DOCUMENTATION','DONE')
                  AND ((t.planned_for_at<? AND (t.planned_for_at>=?
                         OR t.planned_for_at+t.estimate_minutes*INTERVAL '1 minute'>?))
                      OR t.planned_date=?
                      OR (t.planned_for_at IS NULL AND t.planned_date IS NULL AND t.status<>'DONE'))
                """.formatted(TaskVisibility.memberTasks("t",false)),
                mapper,viewer,projectId,projectId,end,start.minusDays(1),start,date);
        List<Entry> entries = new ArrayList<>(), unscheduled = new ArrayList<>();
        for (Task task : tasks) {
            if (category != null && category != task.planningType()) continue;
            List<Person> responsible = responsible(task.userId(),ordinaryAssignments.get(task.id()),people,byLogin);
            Entry event = new Entry(task.id(),false,task.title(),task.plannedForAt(),
                    TaskInterval.endAt(task.plannedForAt(),task.endTime(),task.estimateMinutes()),
                    task.planningType(),task.status(),responsible);
            add(date,event,entries,unscheduled);
        }
        int lookback = recurring.all().stream().filter(t -> projectId == null || projectId.equals(t.projectId()))
                .mapToInt(t -> t.estimateMinutes()==null ? 1 : Math.max(1,Math.ceilDiv(t.estimateMinutes(),1440))).max().orElse(1);
        for (var occurrence : recurring.occurrences(date.minusDays(lookback),date.plusDays(1),projectId)) {
            var task = occurrence.task();
            if (category != null && category != task.planningType()) continue;
            LocalDateTime begins = task.occurrenceTime()==null ? null : occurrence.date().atTime(task.occurrenceTime());
            Entry event = new Entry(task.id(),true,task.title(),begins,
                    TaskInterval.endAt(begins,task.endTime(),task.estimateMinutes()),task.planningType(),
                    occurrence.completed() ? TaskStatus.DONE : task.status(),
                    responsible(task.userId(),regularAssignments.get(task.id()),people,byLogin));
            if (begins != null && event.end()!=null || occurrence.date().equals(date))
                add(date,event,entries,unscheduled);
        }
        List<Column> columns = people.stream().sorted(Comparator.comparing((Person p)->!p.id().equals(viewer))
                .thenComparing(Person::login)).map(p -> ScheduleLayout.column(p,viewer,date,entries)).toList();
        unscheduled.sort(Comparator.comparing(Entry::title,String.CASE_INSENSITIVE_ORDER));
        return new ScheduleView(date,columns,List.copyOf(unscheduled));
    }

    private static void add(LocalDate date, Entry event, List<Entry> timed, List<Entry> unscheduled) {
        if (event.start()==null || event.end()==null || event.people().isEmpty()) {
            if (event.start()==null || event.start().toLocalDate().equals(date)) unscheduled.add(event);
        }
        else if (event.start().isBefore(date.plusDays(1).atStartOfDay()) && event.end().isAfter(date.atStartOfDay())) timed.add(event);
    }

    private static List<Person> responsible(Long author,List<String> logins,List<Person> people,Map<String,Person> byLogin) {
        List<Person> assigned = logins==null ? List.of() : logins.stream().map(byLogin::get).filter(Objects::nonNull).toList();
        return assigned.isEmpty() ? people.stream().filter(p -> p.id().equals(author)).toList() : assigned;
    }
}
