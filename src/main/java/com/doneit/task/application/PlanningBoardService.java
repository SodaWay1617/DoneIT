package com.doneit.task.application;

import com.doneit.project.application.*;
import com.doneit.recurring.application.RegularTaskService.Occurrence;
import com.doneit.tag.application.TagService;
import com.doneit.task.application.command.*;
import com.doneit.task.application.view.*;
import com.doneit.task.domain.*;
import com.doneit.task.infrastructure.persistence.TaskQueueRepository;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Transactional(readOnly=true)
public class PlanningBoardService {
    private final TaskApplicationService tasks;
    private final TaskQueueRepository queues;
    private final ProjectService projects;
    private final TagService tags;
    private final JdbcTemplate db;
    public PlanningBoardService(TaskApplicationService tasks,TaskQueueRepository queues,ProjectService projects,TagService tags,JdbcTemplate db) {
        this.tasks=tasks;this.queues=queues;this.projects=projects;this.tags=tags;this.db=db;
    }
    public record Card(Long id,String key,String title,String project,TaskPriority priority,PlanningType category,
            Integer estimate,LocalDate date,LocalTime time,String interval,boolean recurring,boolean mutable,
            boolean incoming,String state,boolean deadline,List<TagService.Tag> tags,String editHref) {}
    public record Queue(List<Card> cards,long total,long page,long pages) {}
    public record Day(CalendarOverview.Day summary,List<Card> timed,List<Card> untimed) {}
    public Queue queue(boolean backlog,Long project,long page,String back) {
        if(project!=null && !projects.canAccess(project))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var user=projects.user();
        var source=queues.ordinaryPage(user.id(),backlog,project,25,page);
        var visibleTags=tags.visible(false,source.rows().stream().map(TaskQueueRepository.Entry::id).toList());
        return new Queue(source.rows().stream().map(t->new Card(t.id(),"task-"+t.id(),
                TaskListItemView.displayTitle(t.taskKey(),t.title(),user.showProjectInTaskTitle()),t.project(),
                TaskPriority.valueOf(t.priority()),PlanningType.valueOf(t.planningType()),t.estimateMinutes(),null,null,"",
                false,true,!backlog,"open",false,visibleTags.getOrDefault(t.id(),List.of()),editor(t.id(),back))).toList(),
                source.paging().total(),source.paging().number(),source.paging().pages());
    }
    private record Meta(LocalDate date,LocalTime time,LocalTime end,Integer estimate,TaskStatus status) {}
    public List<Day> days(CalendarWeekView week,List<Occurrence> occurrences,String back) {
        var ids=week.days().stream().flatMap(d->d.cards().stream()).filter(c->!c.recurring()).map(c->Long.valueOf(c.key().substring(5))).distinct().toList();
        Map<Long,Meta> meta=new HashMap<>();
        if(!ids.isEmpty()) {
            var args=new ArrayList<Object>();args.add(projects.user().id());args.addAll(ids);
            db.query("SELECT t.id,t.planned_for_at,t.planned_date,t.end_time,t.estimate_minutes,t.status FROM tasks t WHERE "
                    +TaskVisibility.memberTasks("t",false)+" AND t.id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")",
                    rs->{var at=rs.getObject("planned_for_at",LocalDateTime.class);
                        meta.put(rs.getLong("id"),new Meta(at==null?rs.getObject("planned_date",LocalDate.class):at.toLocalDate(),
                                at==null?null:at.toLocalTime(),rs.getObject("end_time",LocalTime.class),rs.getObject("estimate_minutes",Integer.class),TaskStatus.valueOf(rs.getString("status"))));},args.toArray());
        }
        Map<String,Occurrence> regular=new HashMap<>();
        for(var o:occurrences)regular.put(o.date()+":"+o.task().id(),o);
        List<Day> result=new ArrayList<>();
        for(var d:week.days()) {
            List<Card> cards=new ArrayList<>();LocalDate date=d.summary().source().date();
            for(var c:d.cards()) {
                Long id=Long.valueOf(c.key().substring(c.key().indexOf('-')+1));
                var r=c.recurring()?regular.get(date+":"+id):null;var m=meta.get(id);
                if(!c.recurring() && m==null)continue;
                LocalDate planned=c.recurring()?date:m.date();
                LocalTime time=c.recurring()?c.time():Objects.equals(planned,date)?m.time():c.time();
                Integer estimate=c.recurring()?(r==null?null:r.task().estimateMinutes()):m.estimate();
                LocalTime end=c.recurring()?(r==null?null:r.task().endTime()):Objects.equals(planned,date)?m.end():null;
                boolean mutable=!c.recurring() && "open".equals(c.state()) && Objects.equals(planned,date) && PlanningLoad.active(m.status());
                cards.add(new Card(id,c.key(),c.title(),c.project(),c.priority(),c.planningType(),estimate,planned,time,
                        interval(date,time,end,Objects.equals(planned,date)?estimate:null),c.recurring(),mutable,false,c.state(),c.deadline(),List.of(),c.recurring()?"":editor(id,back)));
            }
            cards.sort(Comparator.comparing(Card::time,Comparator.nullsLast(Comparator.naturalOrder())));
            result.add(new Day(d.summary(),cards.stream().filter(c->c.time()!=null).toList(),cards.stream().filter(c->c.time()==null).toList()));
        }
        return List.copyOf(result);
    }
    private static String interval(LocalDate date,LocalTime time,LocalTime end,Integer estimate) {
        if(time==null)return "";
        var format=DateTimeFormatter.ofPattern("HH:mm");
        var until=TaskInterval.endAt(date.atTime(time),end,estimate);
        if(until==null)return time.format(format);
        long extra=java.time.temporal.ChronoUnit.DAYS.between(date,until.toLocalDate());
        return time.format(format)+"–"+until.format(format)+(extra>0?" (+"+extra+")":"");
    }
    public static String editor(Long id,String back) {
        return UriComponentsBuilder.fromPath("/tasks/"+id+"/edit").queryParam("redirectTo",back).build().encode().toUriString();
    }
    private TaskFormView editable(Long id,boolean scheduling) {
        var t=tasks.getTaskForEdit(id);
        if(t.status().isFinished() || scheduling && t.status()==TaskStatus.NEW)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Prepare incoming tasks in the backlog first; finished tasks are read-only");
        return t;
    }
    @Transactional
    public void schedule(Long id,LocalDate date,LocalTime time) {
        var t=editable(id,true);
        if(date==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A date is required");
        if(t.status()==TaskStatus.PAUSED) {
            tasks.editTask(new EditTaskCommand(id,t.title(),t.description(),time==null?null:date.atTime(time),t.deadlineAt(),
                    t.projectId(),time==null?date:null,false,t.priority(),TaskStatus.TODO,t.estimateMinutes(),t.spentMinutes(),t.planningType(),null));
        } else tasks.moveTask(new RescheduleTaskCommand(id,time==null?null:date.atTime(time),time==null?date:null));
    }
    @Transactional
    public void backlog(Long id) {editable(id,false);tasks.moveTaskToBacklog(new MoveTaskToBacklogCommand(id));}
}
