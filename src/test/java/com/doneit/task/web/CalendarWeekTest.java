package com.doneit.task.web;

import com.doneit.support.IntegrationTestSupport;
import com.doneit.task.application.view.*;
import com.doneit.task.domain.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CalendarHistoryTest.FixedClockConfiguration.class)
@WithMockUser(username="owner")
class CalendarWeekTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @SpyBean JdbcTemplate db;
    Long project;
    @BeforeEach void setup() throws Exception {
        db.execute("TRUNCATE users CASCADE");
        db.update("INSERT INTO users(login,password_hash,created_at,updated_at) VALUES('owner','hash',NOW(),NOW()),('other','hash',NOW(),NOW())");
        mvc.perform(post("/projects").with(csrf()).param("name","Weekly project").param("code","WEEK")).andExpect(status().is3xxRedirection());
        project=db.queryForObject("SELECT id FROM projects WHERE code='WEEK'",Long.class);
    }

    @Test void weekIsExactlySevenDaysAndNavigationCrossesMonthsAndYears() throws Exception {
        var result=load("/calendar?mode=week&date=2027-01-01&projectId="+project+"&planningType=WANTED&lang=ru");
        var week=week(result);var links=overview(result);
        assertThat(week.start()).isEqualTo(LocalDate.of(2026,12,28));assertThat(week.end()).isEqualTo(LocalDate.of(2027,1,3));
        assertThat(week.days()).hasSize(7);assertThat(calendar(result).days()).hasSize(7);
        assertThat(links.monthTitle()).contains("2026","2027");
        assertThat(links.previousHref()).contains("week=2026-12-21","projectId="+project,"planningType=WANTED");
        assertThat(links.nextHref()).contains("week=2027-01-04");
        assertThat(links.monthHref()).contains("month=2027-01","mode=month","date=2027-01-01");
        assertThat(links.todayHref()).contains("week=2026-04-06","date=2026-04-08","mode=week");
        String html=main(result);assertThat(html).contains("cw-week","type=\"date\"","data-start=\"2026-12-28\"","id=\"week-day-2027-01-01\"").doesNotContain("mc-grid","mc-week-link","view-filters");
        var today=week(load("/calendar?mode=week"));
        assertThat(today.start()).isEqualTo(LocalDate.of(2026,4,6));assertThat(today.today()).isEqualTo(LocalDate.of(2026,4,8));
        assertThat(today.days()).filteredOn(d->d.summary().source().today()).hasSize(1);
    }

    @Test void timedCardsCategoriesPrioritiesAndDeadlineDeduplicationUseActualValues() throws Exception {
        Long first=task("Morning","2026-04-08","09:00",30,"HIGH","WANTED",null);
        Long duo=task("Critical due","2026-04-08","16:45",60,"CRITICAL","REQUIRED","2026-04-08T20:00");
        Long untimed=task("No invented midnight","2026-04-08",null,15,"LOW","OPTIONAL",null);
        Long separate=task("Separate deadline","2026-04-08",null,10,"NORMAL","REQUIRED","2026-04-10T12:00");
        Long series=regular("Regular morning");
        var result=load("/calendar?mode=week&week=2026-04-06&projectId="+project);
        var day=day(result,"2026-04-08");
        assertThat(day.cards()).extracting(CalendarWeekView.Card::key).containsExactly("task-"+first,"regular-"+series,"task-"+duo,"task-"+separate,"task-"+untimed);
        var card=day.cards().stream().filter(c->c.key().equals("task-"+duo)).findFirst().orElseThrow();
        assertThat(card.deadline()).isTrue();assertThat(card.critical()).isTrue();assertThat(card.time()).isEqualTo(LocalTime.of(16,45));
        assertThat(card.planningType()).isEqualTo(PlanningType.REQUIRED);assertThat(card.priority()).isEqualTo(TaskPriority.CRITICAL);
        assertThat(card.project()).isEqualTo("Weekly project");
        assertThat(day.cards()).filteredOn(c->c.key().equals("task-"+duo)).hasSize(1);
        assertThat(day.cards()).filteredOn(c->c.key().equals("task-"+untimed)).allSatisfy(c->assertThat(c.time()).isNull());
        assertThat(day(result,"2026-04-10").cards()).anySatisfy(c->{assertThat(c.key()).isEqualTo("task-"+separate);assertThat(c.deadline()).isTrue();assertThat(c.time()).isEqualTo(LocalTime.NOON);});
        assertThat(day.summary().minutes()).isEqualTo(130);assertThat(day.summary().count()).isEqualTo(5);
        String html=main(result);assertThat(html).contains("priority-critical","planning-wanted","category-required").doesNotContain("task-tags","task-assignees");
        assertThat(result.getModelAndView().getModel().get("taskAssignees")).isEqualTo(java.util.Map.of());
    }

    @Test void doneAndClosedStayDistinctAndCompletionDoesNotInventAStartTime() throws Exception {
        Long done=task("Completed without time","2026-04-08",null,20,"NORMAL","REQUIRED",null);
        Long closed=task("Closed without time","2026-04-08",null,20,"NORMAL","REQUIRED",null);
        mvc.perform(post("/tasks/{id}/done",done).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(post("/tasks/{id}/closed",closed).with(csrf())).andExpect(status().is3xxRedirection());
        Long series=regular("Recurring history");
        db.update("INSERT INTO regular_task_completions VALUES(?,DATE '2026-04-09',TIMESTAMP '2026-04-09 10:00')",series);
        db.update("UPDATE regular_tasks SET status='DONE',regular_status='INACTIVE',finished_at=TIMESTAMP '2026-04-10 12:00' WHERE id=?",series);
        var result=load("/calendar?mode=week&week=2026-04-06");
        assertThat(day(result,"2026-04-08").cards()).anySatisfy(c->{assertThat(c.key()).isEqualTo("task-"+done);assertThat(c.state()).isEqualTo("done");assertThat(c.time()).isNull();});
        assertThat(day(result,"2026-04-08").cards()).anySatisfy(c->{assertThat(c.key()).isEqualTo("task-"+closed);assertThat(c.state()).isEqualTo("closed");assertThat(c.time()).isNull();});
        assertThat(day(result,"2026-04-09").cards()).anySatisfy(c->{assertThat(c.key()).isEqualTo("regular-"+series);assertThat(c.state()).isEqualTo("done");});
        assertThat(day(result,"2026-04-08").cards()).anySatisfy(c->{assertThat(c.key()).isEqualTo("regular-"+series);assertThat(c.state()).isEqualTo("open");});
        assertThat(main(result)).contains("circle-check","circle-x").doesNotContain("type=\"checkbox\"");
    }

    @Test void editorReturnsToOriginalWeekEvenWhenTaskMovesElsewhere() throws Exception {
        Long id=task("Move me","2026-04-08",null,30,"NORMAL","REQUIRED",null);
        String source="/calendar?mode=week&week=2026-04-06&date=2026-04-08&projectId="+project+"&planningType=REQUIRED";
        var result=load(source);String back=overview(result).returnTo();
        var card=day(result,"2026-04-08").cards().getFirst();
        String editor=mvc.perform(get(java.net.URI.create(card.href()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(editor).contains("name=\"redirectTo\" value=\""+back.replace("&","&amp;")+"\"","href=\""+back.replace("&","&amp;")+"\"");
        mvc.perform(post("/tasks/{id}",id).with(csrf()).param("title","Moved").param("status","TODO").param("projectId",project.toString())
                .param("plannedDate","2026-04-15").param("redirectTo",back)).andExpect(redirectedUrl(back));
        assertThat(week(load(back)).start()).isEqualTo(LocalDate.of(2026,4,6));
        assertThat(week(load(back)).days().stream().flatMap(d->d.cards().stream())).noneMatch(c->c.key().equals("task-"+id));
        assertThat(day(load("/calendar?mode=week&week=2026-04-13"),"2026-04-15").cards()).anyMatch(c->c.key().equals("task-"+id));
    }

    @Test void periodIsBoundedAndProjectsCannotExposeHiddenData() throws Exception {
        task("Outside week","2026-05-01",null,30,"CRITICAL","REQUIRED",null);
        task("Own week","2026-04-08",null,30,"NORMAL","REQUIRED",null);
        regular("Own series");
        var result=load("/calendar?mode=week&week=2026-04-06");
        assertThat(main(result)).contains("Own week").doesNotContain("Outside week");
        var occurrences=(List<com.doneit.recurring.application.RegularTaskService.Occurrence>)result.getModelAndView().getModel().get("regularCalendar");
        assertThat(occurrences).allSatisfy(o->assertThat(o.date()).isBetween(LocalDate.of(2026,4,6),LocalDate.of(2026,4,12)));
        var foreign=mvc.perform(get("/calendar?mode=week&week=2026-04-06").with(user("other"))).andExpect(status().isOk()).andReturn();
        assertThat(week(foreign).days().stream().flatMap(d->d.cards().stream())).isEmpty();
        mvc.perform(get("/calendar?mode=week&projectId="+project).with(user("other"))).andExpect(status().isNotFound());
    }

    @Test void calendarQueriesDoNotGrowWithEachCardAndMonthKeepsItsFullGrid() throws Exception {
        task("First","2026-04-08",null,10,"NORMAL","REQUIRED",null);
        org.mockito.Mockito.clearInvocations(db);load("/calendar?mode=week&week=2026-04-06");long first=queryCount();
        for(int n=0;n<10;n++)task("Extra "+n,"2026-04-08",null,10,"NORMAL","REQUIRED",null);
        org.mockito.Mockito.clearInvocations(db);var result=load("/calendar?mode=week&week=2026-04-06");long second=queryCount();
        assertThat(second).isLessThanOrEqualTo(first+2);assertThat(day(result,"2026-04-08").cards()).hasSize(11);
        var month=load("/calendar?month=2026-04");assertThat(calendar(month).days()).hasSize(35);
        assertThat(main(month)).contains("mc-grid").doesNotContain("cw-week");
    }

    private long queryCount() {return org.mockito.Mockito.mockingDetails(db).getInvocations().stream().filter(i->i.getMethod().getName().startsWith("query")).count();}
    private MvcResult load(String path)throws Exception {return mvc.perform(get(path)).andExpect(status().isOk()).andReturn();}
    private CalendarWeekView week(MvcResult r) {return (CalendarWeekView)r.getModelAndView().getModel().get("weekView");}
    private CalendarOverview overview(MvcResult r) {return (CalendarOverview)r.getModelAndView().getModel().get("overview");}
    private CalendarMonthView calendar(MvcResult r) {return (CalendarMonthView)r.getModelAndView().getModel().get("calendar");}
    private CalendarWeekView.Day day(MvcResult r,String date) {return week(r).days().stream().filter(d->d.summary().source().date().equals(LocalDate.parse(date))).findFirst().orElseThrow();}
    private String main(MvcResult r)throws Exception {String html=r.getResponse().getContentAsString();return html.substring(html.indexOf("<main"),html.indexOf("</main>"));}
    private Long regular(String title)throws Exception {
        mvc.perform(post("/recurring").with(csrf()).param("title",title).param("status","IN_PROGRESS").param("projectId",project.toString())
                .param("recurrenceType","DAILY").param("occurrenceTime","09:30").param("estimateMinutes","15").param("priority","HIGH").param("planningType","WANTED")).andExpect(status().is3xxRedirection());
        return db.queryForObject("SELECT id FROM regular_tasks WHERE title=?",Long.class,title);
    }
    private Long task(String title,String date,String time,int estimate,String priority,String planning,String deadline)throws Exception {
        var request=post("/tasks").with(csrf()).param("title",title).param("status","TODO").param("projectId",project.toString())
                .param("estimateMinutes",Integer.toString(estimate)).param("priority",priority).param("planningType",planning);
        if(time==null)request.param("plannedDate",date);else request.param("plannedWithTime","true").param("plannedForAt",date+"T"+time);
        if(deadline!=null)request.param("deadlineAt",deadline);
        mvc.perform(request).andExpect(status().is3xxRedirection());
        return db.queryForObject("SELECT id FROM tasks WHERE title=?",Long.class,title);
    }
}
