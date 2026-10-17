package com.doneit.notifications.application;

import com.doneit.project.application.ProjectService;
import com.doneit.project.application.TaskVisibility;
import com.doneit.recurring.application.RegularTaskService;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class SiteReminderService {
    private final JdbcTemplate db;
    private final ProjectService projects;
    private final RegularTaskService regular;
    private final Clock clock;
    private final MessageSource messages;
    public SiteReminderService(JdbcTemplate db,ProjectService projects,RegularTaskService regular,Clock clock,MessageSource messages) {
        this.db=db;this.projects=projects;this.regular=regular;this.clock=clock;this.messages=messages;
    }
    public Reminders current(Locale locale) {
        var user=projects.user();LocalDate today=LocalDate.now(clock);
        List<Candidate> candidates=new ArrayList<>(db.query("""
                SELECT t.id,t.title,t.planned_for_at,t.planned_date,t.deadline_at,t.priority,t.status
                FROM tasks t WHERE t.status NOT IN ('DONE','CLOSED') AND %s
                AND (t.deadline_at<? OR t.planned_for_at::date=?
                  OR (t.priority IN ('HIGH','CRITICAL') AND COALESCE(t.planned_date,t.planned_for_at::date)>?
                    AND COALESCE(t.planned_date,t.planned_for_at::date)<=?))
                """.formatted(TaskVisibility.memberTasks("t",false,true)),(r,n)-> {
                    LocalDateTime deadline=r.getObject("deadline_at",LocalDateTime.class);
                    LocalDateTime planned=r.getObject("planned_for_at",LocalDateTime.class);
                    LocalDate date=r.getObject("planned_date",LocalDate.class);
                    if(date==null&&planned!=null) date=planned.toLocalDate();
                    Kind kind=null;LocalDate eventDate=null;LocalTime time=null;
                    if(deadline!=null&&!deadline.toLocalDate().isAfter(today.plusDays(1))) {
                        eventDate=deadline.toLocalDate();
                        kind=eventDate.isBefore(today)?Kind.OVERDUE:eventDate.equals(today)?Kind.DEADLINE_TODAY:Kind.DEADLINE_TOMORROW;
                    } else if(Set.of("SPECIFICATION","TODO","IN_PROGRESS","DOCUMENTATION").contains(r.getString("status"))) {
                        if(planned!=null&&date.equals(today)) {kind=Kind.SCHEDULED;eventDate=today;time=planned.toLocalTime();}
                        else if(date!=null&&date.isAfter(today)&&!date.isAfter(today.plusDays(7))
                                &&Set.of("HIGH","CRITICAL").contains(r.getString("priority"))) {kind=Kind.UPCOMING;eventDate=date;}
                    }
                    return kind==null?null:new Candidate(r.getLong("id"),false,r.getString("title"),kind,eventDate,time);
                },user.id(),today.plusDays(2).atStartOfDay(),today,today,today.plusDays(7)).stream().filter(Objects::nonNull).toList());
        Set<Long> personal=projects.personalTaskIds(true);
        for(var task:regular.activeFor(today,null)) if(personal.contains(task.id())&&task.occurrenceTime()!=null)
            candidates.add(new Candidate(task.id(),true,task.title(),Kind.SCHEDULED,today,task.occurrenceTime()));
        candidates.sort(Comparator.comparing(Candidate::kind).thenComparing(Candidate::date)
                .thenComparing(Candidate::time,Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Candidate::title,String.CASE_INSENSITIVE_ORDER).thenComparing(Candidate::id));
        var items=candidates.stream().limit(5).map(c->new Reminder(c.title(),
                messages.getMessage("reminders."+c.kind().name(),new Object[]{c.time()==null?
                        c.date().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")):c.time().format(DateTimeFormatter.ofPattern("HH:mm"))},locale),
                (c.recurring()?"/recurring/":"/tasks/")+c.id()+"/edit")).toList();
        return new Reminders(user.id(),today,messages.getMessage("reminders.title",null,locale),
                messages.getMessage("reminders.dismiss",null,locale),candidates.size()>5?
                    messages.getMessage("reminders.more",new Object[]{candidates.size()-5},locale):null,items);
    }
    private enum Kind { OVERDUE,DEADLINE_TODAY,DEADLINE_TOMORROW,SCHEDULED,UPCOMING }
    private record Candidate(Long id,boolean recurring,String title,Kind kind,LocalDate date,LocalTime time) {}
    public record Reminder(String title,String message,String href) {}
    public record Reminders(Long userId,LocalDate day,String heading,String dismissLabel,String more,List<Reminder> items) {}
}
