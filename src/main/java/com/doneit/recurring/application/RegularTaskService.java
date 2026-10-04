package com.doneit.recurring.application;

import com.doneit.project.application.ProjectService;
import com.doneit.project.domain.Project;
import com.doneit.recurring.domain.*;
import com.doneit.task.domain.*;
import com.doneit.user.domain.User;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class RegularTaskService {
 private static final List<TaskStatus> ACTIVE=List.of(TaskStatus.SPECIFICATION,TaskStatus.IN_PROGRESS,TaskStatus.DOCUMENTATION);
 private final JdbcTemplate db; private final ProjectService projects; private final Clock clock;
 public RegularTaskService(JdbcTemplate db,ProjectService projects,Clock clock){this.db=db;this.projects=projects;this.clock=clock;}
 @Transactional public void create(RegularTaskForm f){
  validate(f,true); User u=projects.user(); Project p=projects.get(f.getProjectId()); LocalDateTime now=LocalDateTime.now(clock);
  Long n=db.queryForObject("UPDATE projects SET next_task_number=next_task_number+1 WHERE id=? RETURNING next_task_number-1",Long.class,p.id());
  String key=(p.defaultProject()?"MAIN-"+u.login():p.code())+"-"+n+"___"+u.login();
  RegularStatus rs=active(f.getStatus())?RegularStatus.ACTIVE:RegularStatus.INACTIVE;
  Long pos=db.queryForObject("SELECT COALESCE(MAX(status_position),0)+1 FROM regular_tasks WHERE status=?",Long.class,f.getStatus().name());
  db.update("INSERT INTO regular_tasks(user_id,project_id,task_number,task_key,title,description,status,priority,recurrence_type,repeat_interval,occurrence_time,anchor_date,regular_status,activated_at,finished_at,created_at,updated_at,status_position,estimate_minutes) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
   u.id(),p.id(),n,key,f.getTitle().trim(),blank(f.getDescription()),f.getStatus().name(),f.getPriority().name(),f.getRecurrenceType().name(),interval(f),f.getOccurrenceTime(),anchor(f),rs.name(),rs==RegularStatus.ACTIVE?now:null,null,now,now,pos,f.getEstimateMinutes());
 }
 public RegularTaskForm form(Long id){RegularTask t=get(id);RegularTaskForm f=new RegularTaskForm();f.setId(t.id());f.setTitle(t.title());f.setDescription(t.description());f.setProjectId(t.projectId());f.setStatus(t.status());f.setPriority(t.priority());f.setRecurrenceType(t.recurrenceType());f.setRepeatInterval(t.repeatInterval());f.setOccurrenceTime(t.occurrenceTime());f.setAnchorDate(t.anchorDate());f.setEstimateMinutes(t.estimateMinutes());f.setSpentMinutes(t.spentMinutes());return f;}
 @Transactional public void update(Long id,RegularTaskForm f){
  validate(f,false);RegularTask old=get(id);projects.get(f.getProjectId());transition(old,f.getStatus());LocalDateTime now=LocalDateTime.now(clock);
  boolean activating=old.regularStatus()==RegularStatus.INACTIVE&&active(f.getStatus()),finishing=f.getStatus().isFinished();
  RegularStatus rs=finishing?RegularStatus.INACTIVE:(activating?RegularStatus.ACTIVE:old.regularStatus());
  db.update("UPDATE regular_tasks SET project_id=?,title=?,description=?,status=?,priority=?,recurrence_type=?,repeat_interval=?,occurrence_time=?,anchor_date=?,regular_status=?,activated_at=?,finished_at=?,estimate_minutes=?,updated_at=? WHERE id=?",
   f.getProjectId(),f.getTitle().trim(),blank(f.getDescription()),f.getStatus().name(),f.getPriority().name(),f.getRecurrenceType().name(),interval(f),f.getOccurrenceTime(),anchor(f),rs.name(),activating?now:old.activatedAt(),finishing?now:old.finishedAt(),f.getEstimateMinutes(),now,id);
  int adjustment=f.getSpentMinutes()-old.spentMinutes();
  if(adjustment!=0)addTimeEntry(id,LocalDate.now(clock),adjustment);
 }
 @Transactional public void complete(Long id,LocalDate date){complete(id,date,null);}
 @Transactional public void complete(Long id,LocalDate date,Integer minutes){RegularTask t=get(id);if(!t.occursOn(date)||t.status().isFinished())throw new IllegalArgumentException();validateMinutes(minutes);int inserted=db.update("INSERT INTO regular_task_completions VALUES(?,?,?) ON CONFLICT DO NOTHING",id,date,LocalDateTime.now(clock));if(inserted>0&&minutes!=null)addTimeEntry(id,date,minutes);}
 @Transactional public void skipToday(Long id){LocalDate date=today();RegularTask t=get(id);if(!active(t.status())||!t.occursOn(date)||completed(id,date))throw new IllegalArgumentException("No active recurring occurrence today");db.update("INSERT INTO regular_task_skips VALUES(?,?,?) ON CONFLICT DO NOTHING",id,date,LocalDateTime.now(clock));}
 @Transactional public void track(Long id,LocalDate date,int minutes){RegularTask task=get(id);validateMinutes(minutes);if(!task.occursOn(date)||task.status().isFinished())throw new IllegalArgumentException("No recurring occurrence on this date");addTimeEntry(id,date,minutes);}
 public List<RegularTask> all(){return db.query("SELECT r.*,COALESCE((SELECT SUM(e.minutes) FROM regular_task_time_entries e WHERE e.regular_task_id=r.id),0) spent_minutes FROM regular_tasks r JOIN project_members m ON m.project_id=r.project_id WHERE m.user_id=? ORDER BY r.status_position,r.id",RegularTaskService::map,projects.user().id());}
 public List<RegularTask> activeFor(LocalDate date,Long projectId){return all().stream().filter(t->match(t,projectId)&&active(t.status())&&t.occursOn(date)&&!completed(t.id(),date)&&!skipped(t.id(),date)).toList();}
 public List<RegularTask> inbox(Long projectId){return all().stream().filter(t->match(t,projectId)&&t.status()==TaskStatus.NEW).toList();}
 public List<RegularTask> backlog(Long projectId){return all().stream().filter(t->match(t,projectId)&&t.status()==TaskStatus.BACKLOG).toList();}
 public List<RegularTask> finished(Long projectId){return all().stream().filter(t->match(t,projectId)&&t.status().isFinished()).toList();}
 public List<RegularTask> kanbanFor(LocalDate date,Long projectId){return all().stream().filter(t->match(t,projectId)).filter(t->t.status().isFinished()?t.finishedAt()!=null&&t.finishedAt().toLocalDate().equals(date):t.regularStatus()==RegularStatus.INACTIVE||(t.occursOn(date)&&!completed(t.id(),date)&&!skipped(t.id(),date))).toList();}
 public List<Occurrence> occurrences(LocalDate start,LocalDate end,Long projectId){List<Occurrence> out=new ArrayList<>();for(RegularTask t:all())if(match(t,projectId)&&active(t.status())&&t.activatedAt()!=null)for(LocalDate d=start;d.isBefore(end);d=d.plusDays(1))if(t.occursOn(d)&&!skipped(t.id(),d))out.add(new Occurrence(t,d,completed(t.id(),d)));return out;}
 public record Occurrence(RegularTask task,LocalDate date,boolean completed){}
 public boolean completed(Long id,LocalDate d){Integer n=db.queryForObject("SELECT COUNT(*) FROM regular_task_completions WHERE regular_task_id=? AND occurrence_date=?",Integer.class,id,d);return n!=null&&n>0;}
 public boolean skipped(Long id,LocalDate d){Integer n=db.queryForObject("SELECT COUNT(*) FROM regular_task_skips WHERE regular_task_id=? AND occurrence_date=?",Integer.class,id,d);return n!=null&&n>0;}
 public LocalDate today(){return LocalDate.now(clock);}
 public static boolean active(TaskStatus s){return ACTIVE.contains(s);}
 private static boolean match(RegularTask t,Long projectId){return projectId==null||projectId.equals(t.projectId());}
 private RegularTask get(Long id){return db.query("SELECT r.*,COALESCE((SELECT SUM(e.minutes) FROM regular_task_time_entries e WHERE e.regular_task_id=r.id),0) spent_minutes FROM regular_tasks r JOIN project_members m ON m.project_id=r.project_id WHERE r.id=? AND m.user_id=?",RegularTaskService::map,id,projects.user().id()).stream().findFirst().orElseThrow();}
 private static RegularTask map(ResultSet r,int row)throws SQLException{return new RegularTask(r.getLong("id"),r.getLong("user_id"),r.getLong("project_id"),r.getLong("task_number"),r.getString("task_key"),r.getString("title"),r.getString("description"),TaskStatus.valueOf(r.getString("status")),TaskPriority.valueOf(r.getString("priority")),RecurrenceType.valueOf(r.getString("recurrence_type")),r.getInt("repeat_interval"),r.getObject("occurrence_time",LocalTime.class),r.getObject("anchor_date",LocalDate.class),RegularStatus.valueOf(r.getString("regular_status")),r.getObject("activated_at",LocalDateTime.class),r.getObject("finished_at",LocalDateTime.class),r.getObject("created_at",LocalDateTime.class),r.getObject("updated_at",LocalDateTime.class),r.getLong("status_position"),(Integer)r.getObject("estimate_minutes"),r.getInt("spent_minutes"));}
 private static void validate(RegularTaskForm f,boolean create){if(f.getStatus()==TaskStatus.TODO||f.getStatus()==TaskStatus.PAUSED)throw new IllegalArgumentException("Status unavailable");if(create&&f.getStatus().isFinished())throw new IllegalArgumentException("Cannot create finished");if((f.getRecurrenceType().requiresAnchorDate()||f.getRepeatInterval()>1)&&f.getAnchorDate()==null)throw new IllegalArgumentException("Anchor date required");if(f.getRecurrenceType()==RecurrenceType.WEEKDAYS&&f.getRepeatInterval()!=1)throw new IllegalArgumentException("Weekday interval must be one");if(f.getSpentMinutes()<0)throw new IllegalArgumentException("Spent time cannot be negative");}
 private static void transition(RegularTask t,TaskStatus n){if(t.status().isFinished())throw new IllegalArgumentException("Finished task is immutable");if(t.regularStatus()==RegularStatus.ACTIVE&&(n==TaskStatus.NEW||n==TaskStatus.BACKLOG))throw new IllegalArgumentException("Cannot deactivate recurring task");}
 private static LocalDate anchor(RegularTaskForm f){return f.getAnchorDate();}
 private static int interval(RegularTaskForm f){return f.getRecurrenceType()==RecurrenceType.WEEKDAYS?1:f.getRepeatInterval();}
 private static String blank(String s){return s==null||s.isBlank()?null:s.trim();}
 private static void validateMinutes(Integer minutes){if(minutes!=null&&minutes<=0)throw new IllegalArgumentException("Tracked time must be positive");}
 private void addTimeEntry(Long id,LocalDate date,int minutes){db.update("INSERT INTO regular_task_time_entries(regular_task_id,user_id,entry_date,minutes,created_at) VALUES(?,?,?,?,?)",id,projects.user().id(),date,minutes,LocalDateTime.now(clock));}
}
