package com.doneit.task.application;

import com.doneit.project.application.ProjectService;
import com.doneit.project.application.TaskVisibility;
import com.doneit.project.domain.Project;
import com.doneit.recurring.application.RegularTaskService.Occurrence;
import com.doneit.task.application.view.*;
import com.doneit.task.application.view.CalendarWeekView.*;
import com.doneit.task.domain.TaskStatus;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Transactional(readOnly=true)
public class CalendarWeekService {
    private final JdbcTemplate db;
    private final ProjectService projects;
    private final Clock clock;
    public CalendarWeekService(JdbcTemplate db,ProjectService projects,Clock clock) { this.db=db;this.projects=projects;this.clock=clock; }
    private record Meta(String project,LocalTime time,LocalTime deadline) {}

    public CalendarWeekView build(CalendarMonthView calendar,CalendarOverview overview,List<Occurrence> occurrences,
            List<Project> available,LocalDate anchor,LocalDate focus) {
        var ids=calendar.days().stream().flatMap(d->d.items().stream()).map(CalendarItemView::taskId).distinct().toList();
        Map<Long,Meta> metadata=new HashMap<>();
        if(!ids.isEmpty()) {
            var args=new ArrayList<Object>();args.add(projects.user().id());args.addAll(ids);
            db.query("SELECT t.id,p.name,p.default_project,t.planned_for_at,t.deadline_at FROM tasks t JOIN projects p ON p.id=t.project_id WHERE "
                    +TaskVisibility.memberTasks("t",false)+" AND t.id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")",
                    rs->{
                        LocalDateTime planned=rs.getObject("planned_for_at",LocalDateTime.class),deadline=rs.getObject("deadline_at",LocalDateTime.class);
                        metadata.put(rs.getLong("id"),new Meta(rs.getBoolean("default_project")?"MAIN":rs.getString("name").split("___",2)[0],
                                planned==null?null:planned.toLocalTime(),deadline==null?null:deadline.toLocalTime()));
                    },args.toArray());
        }
        Map<Long,String> names=available.stream().collect(Collectors.toMap(Project::id,p->p.defaultProject()?"MAIN":p.name().split("___",2)[0]));
        Map<LocalDate,List<Occurrence>> regular=occurrences.stream().collect(Collectors.groupingBy(Occurrence::date));
        List<Day> days=new ArrayList<>();
        for(var summary:overview.weeks().getFirst().days()) {
            Map<String,Card> cards=new LinkedHashMap<>();
            for(var item:summary.source().items()) {
                Meta meta=metadata.get(item.taskId());if(meta==null)continue;
                String key="task-"+item.taskId();Card before=cards.get(key);
                boolean deadline=item.deadlineHighlighted()||before!=null&&before.deadline();
                LocalTime time=item.occurrenceType()==CalendarOccurrenceType.DEADLINE?meta.deadline():meta.time();
                String state=item.taskStatus()==TaskStatus.DONE?"done":item.taskStatus()==TaskStatus.CLOSED?"closed":"open";
                cards.put(key,new Card(key,item.title(),meta.project(),time,meta.deadline(),item.priority(),item.planningType(),state,deadline,false,
                        editor("/tasks/"+item.taskId()+"/edit",overview.returnTo())));
            }
            for(var occurrence:regular.getOrDefault(summary.source().date(),List.of())) {
                var task=occurrence.task();String key="regular-"+task.id();
                String state=occurrence.completed()?"done":task.status()==TaskStatus.CLOSED?"closed":"open";
                cards.put(key,new Card(key,task.title(),names.getOrDefault(task.projectId(),""),task.occurrenceTime(),null,
                        task.priority(),task.planningType(),state,false,true,editor("/recurring/"+task.id()+"/edit",overview.returnTo())));
            }
            var ordered=cards.values().stream().sorted(Comparator.comparing((Card c)->c.time()==null)
                    .thenComparing(Card::time,Comparator.nullsLast(Comparator.naturalOrder()))).toList();
            days.add(new Day(summary,ordered));
        }
        return new CalendarWeekView(overview.selectedWeek(),overview.selectedWeek().plusDays(6),LocalDate.now(clock),focus,anchor,List.copyOf(days));
    }
    private static String editor(String path,String back) { return UriComponentsBuilder.fromPath(path).queryParam("redirectTo",back).build().encode().toUriString(); }
}
