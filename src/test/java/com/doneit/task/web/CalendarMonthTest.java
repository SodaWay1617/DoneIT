package com.doneit.task.web;

import com.doneit.navigation.AppNavigationService;
import com.doneit.support.IntegrationTestSupport;
import com.doneit.task.application.view.CalendarOverview;
import com.doneit.task.application.view.CalendarMonthView;
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
class CalendarMonthTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @SpyBean JdbcTemplate db;
    Long project;

    @BeforeEach void setup() {
        db.execute("TRUNCATE users CASCADE");
        db.update("INSERT INTO users(login,password_hash,created_at,updated_at) VALUES('owner','hash',NOW(),NOW()),('other','hash',NOW(),NOW())");
        project=db.queryForObject("SELECT id FROM projects WHERE owner_user_id=(SELECT id FROM users WHERE login='owner')",Long.class);
    }

    @Test void monthsHaveFourFiveOrSixRowsAndIsoWeeksCrossYears() throws Exception {
        for(var sample:List.of(new Object[]{"2021-02",4},new Object[]{"2026-04",5},new Object[]{"2026-11",6})) {
            var result=load("/calendar?month="+sample[0]);var overview=overview(result);
            assertThat(overview.weeks()).hasSize((Integer)sample[1]);
            assertThat(overview.weeks()).allSatisfy(w->{
                assertThat(w.days()).hasSize(7);assertThat(w.start().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
            });
        }
        var november=overview(load("/calendar?month=2026-11"));
        assertThat(november.weeks().getFirst().start()).isEqualTo(LocalDate.of(2026,10,26));
        assertThat(november.weeks().getLast().days().getLast().source().date()).isEqualTo(LocalDate.of(2026,12,6));
        var january=overview(load("/calendar?month=2021-01"));
        assertThat(january.weeks().getFirst().number()).isEqualTo(53);
        assertThat(january.weeks().getFirst().start()).isEqualTo(LocalDate.of(2020,12,28));
        assertThat(january.weeks().get(1).number()).isEqualTo(1);
    }

    @Test void defaultIsCurrentMonthAndControlsPreserveModeProjectAndPlanning() throws Exception {
        var result=load("/calendar?lang=en");var view=overview(result);
        assertThat(view.monthValue()).isEqualTo("2026-04");assertThat(view.mode()).isEqualTo("month");
        assertThat(view.monthTitle()).isEqualTo("April 2026");
        String html=main(result);
        assertThat(html).contains("class=\"mc-grid\"","type=\"month\"").doesNotContain("class=\"hero\"","class=\"view-filters\"","Signed in as","app-view-tabs","class=\"calendar calendar-legacy\"");
        var previous=overview(load("/calendar?month=2026-12&mode=week&week=2026-12-28&projectId="+project+"&planningType=WANTED"));
        assertThat(previous.nextHref()).contains("month=2027-01","mode=week","projectId="+project,"planningType=WANTED");
        assertThat(previous.todayHref()).contains("month=2026-04","mode=week","week=2026-04-06");
        assertThat(previous.monthHref()).contains("mode=month").doesNotContain("week=");
        var nav=(AppNavigationService.Navigation)load("/calendar?month=2026-12&mode=week&week=2026-12-28").getModelAndView().getModel().get("appNav");
        assertThat(nav.projects()).allSatisfy(p->assertThat(p.href()).startsWith("/calendar?").contains("month=2026-12","mode=week","week=2026-12-28"));
        assertThat(overview(load("/calendar?mode=week&week=2026-12-28")).monthValue()).isEqualTo("2026-12");
        assertThat(main(load("/calendar?mode=week"))).contains("calendar-legacy","Week mode temporarily");
    }

    @Test void totalsUseExistingEstimatesAndThresholdsAndEventsAreDeduplicated() throws Exception {
        Long duo=task("Duo","2026-04-08",60,"CRITICAL","2026-04-08T12:00");
        task("Critical","2026-04-08",30,"CRITICAL",null);
        task("Deadline only","2026-04-09",20,"NORMAL","2026-04-08T17:00");
        recurring("Critical series");
        db.update("UPDATE regular_tasks SET priority='CRITICAL',estimate_minutes=15");
        db.update("UPDATE users SET workload_green_minutes=30,workload_yellow_minutes=60,workload_orange_minutes=90 WHERE login='owner'");
        var result=load("/calendar?month=2026-04&projectId="+project);var day=day(result,"2026-04-08");
        assertThat(day.minutes()).isEqualTo(105);assertThat(day.level()).isEqualTo("red");assertThat(day.fill()).isEqualTo(100);
        assertThat(day.count()).isEqualTo(4);
        assertThat(day.important()).hasSize(4);
        assertThat(day.important().getFirst().key()).isEqualTo("task-"+duo);
        assertThat(day.important().getFirst().deadline()).isTrue();assertThat(day.important().getFirst().critical()).isTrue();
        assertThat(day.important()).extracting(CalendarOverview.Important::key).doesNotHaveDuplicates();
        assertThat(day.important().get(1).title()).contains("Deadline only");
        mvc.perform(get(java.net.URI.create(day.important().getFirst().href()))).andExpect(status().isOk())
                .andExpect(model().attribute("calendarReturnTo",overview(result).returnTo()));
        assertThat(day.href()).contains("/tasks?date=2026-04-08","projectId="+project);
        assertThat(day.weekHref()).contains("mode=week","week=2026-04-06");
        Long ordinary=task("Ordinary title must stay hidden","2026-04-08",10,"NORMAL",null);
        assertThat(main(load("/calendar?month=2026-04"))).doesNotContain("Ordinary title must stay hidden");
        assertThat(main(load("/calendar?month=2026-04&mode=week"))).contains("Ordinary title must stay hidden","/tasks/"+ordinary+"/edit");
    }

    @Test void pastDaysUseActualSignedEntriesInsteadOfEstimates() throws Exception {
        Long id=task("Past","2026-04-07",300,"NORMAL",null);
        db.update("INSERT INTO task_time_entries(task_id,user_id,entry_date,minutes,created_at) SELECT ?,id,DATE '2026-04-07',35,NOW() FROM users WHERE login='owner'",id);
        db.update("INSERT INTO task_time_entries(task_id,user_id,entry_date,minutes,created_at) SELECT ?,id,DATE '2026-04-07',-5,NOW() FROM users WHERE login='owner'",id);
        var past=day(load("/calendar?month=2026-04"),"2026-04-07");
        assertThat(past.minutes()).isEqualTo(30);assertThat(past.source().actualWorkload()).isTrue();
        assertThat(day(load("/calendar?month=2026-04"),"2026-04-08").source().today()).isTrue();
    }

    @Test void editorPreservesMonthModeProjectAndWeekOnSaveAndCancel() throws Exception {
        Long id=task("Critical","2026-04-08",45,"CRITICAL",null);
        String back="/calendar?month=2026-04&mode=week&week=2026-04-06&projectId="+project;
        String editor=mvc.perform(get("/tasks/{id}/edit",id).param("redirectTo",back)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(editor).contains("name=\"redirectTo\" value=\""+back.replace("&","&amp;")+"\"","href=\""+back.replace("&","&amp;")+"\"");
        mvc.perform(post("/tasks/{id}",id).with(csrf()).param("title","Edited").param("status","TODO").param("projectId",project.toString())
                .param("plannedDate","2026-04-09").param("redirectTo",back)).andExpect(redirectedUrl(back));
        Long series=recurring("Regular critical");
        mvc.perform(post("/recurring/{id}",series).with(csrf()).param("title","Regular edited").param("status","IN_PROGRESS")
                .param("recurrenceType","DAILY").param("projectId",project.toString()).param("redirectTo",back)).andExpect(redirectedUrl(back));
    }

    @Test void projectsAndInputValidationCannotExposeForeignData() throws Exception {
        Long foreign=db.queryForObject("SELECT id FROM projects WHERE owner_user_id=(SELECT id FROM users WHERE login='other')",Long.class);
        mvc.perform(get("/calendar").param("projectId",foreign.toString())).andExpect(status().isNotFound());
        task("Private critical","2026-04-08",30,"CRITICAL",null);
        var foreignResult=mvc.perform(get("/calendar?month=2026-04").with(user("other"))).andExpect(status().isOk()).andReturn();
        assertThat(main(foreignResult)).doesNotContain("Private critical");
        assertThat(day(foreignResult,"2026-04-08").count()).isZero();
        for(String query:List.of("month=broken","mode=broken","week=broken"))
            mvc.perform(get("/calendar?"+query)).andExpect(status().isBadRequest());
    }

    @Test void regularMarksAreBatchedAndOnlyTheDisplayedRangeIsExpanded() throws Exception {
        Long id=recurring("Series");
        db.update("UPDATE regular_tasks SET activated_at=TIMESTAMP '2020-01-01' WHERE id=?",id);
        org.mockito.Mockito.clearInvocations(db);
        var shortMonth=load("/calendar?month=2021-02");long first=queryCount();
        org.mockito.Mockito.clearInvocations(db);
        var longMonth=load("/calendar?month=2026-11");long second=queryCount();
        assertThat(second).isLessThanOrEqualTo(first+2);
        assertThat(overview(shortMonth).weeks()).hasSize(4);assertThat(overview(longMonth).weeks()).hasSize(6);
        var occurrences=(List<?>)longMonth.getModelAndView().getModel().get("regularCalendar");
        assertThat(occurrences).hasSize(42);
    }

    @Test void batchingPreservesScheduleOnlyCompletionAndSkipVisibility() throws Exception {
        Long id=recurring("Shared schedule series");
        db.update("UPDATE projects SET task_visibility='SCHEDULE_ONLY' WHERE id=?",project);
        db.update("INSERT INTO project_members(project_id,user_id,joined_at) SELECT ?,id,NOW() FROM users WHERE login='other'",project);
        db.update("INSERT INTO regular_task_completions VALUES(?,DATE '2026-04-08',TIMESTAMP '2026-04-08 09:00')",id);
        db.update("INSERT INTO regular_task_skips VALUES(?,DATE '2026-04-09',TIMESTAMP '2026-04-08 09:00')",id);
        var result=mvc.perform(get("/schedule?date=2026-04-08&projectId="+project).with(user("other"))).andExpect(status().isOk()).andReturn();
        var schedule=(com.doneit.schedule.application.ScheduleView)result.getModelAndView().getModel().get("schedule");
        assertThat(schedule.unscheduled()).anySatisfy(e->{assertThat(e.id()).isEqualTo(id);assertThat(e.status()).isEqualTo(com.doneit.task.domain.TaskStatus.DONE);assertThat(e.editable()).isFalse();});
        var skipped=mvc.perform(get("/schedule?date=2026-04-09&projectId="+project).with(user("other"))).andExpect(status().isOk()).andReturn();
        var next=(com.doneit.schedule.application.ScheduleView)skipped.getModelAndView().getModel().get("schedule");
        assertThat(next.unscheduled()).noneMatch(e->e.id().equals(id));
        var calendar=mvc.perform(get("/calendar?month=2026-04&projectId="+project).with(user("other"))).andExpect(status().isOk()).andReturn();
        assertThat(day(calendar,"2026-04-08").count()).isZero();
    }

    private long queryCount() { return org.mockito.Mockito.mockingDetails(db).getInvocations().stream().filter(i->i.getMethod().getName().startsWith("query")).count(); }
    private MvcResult load(String path)throws Exception { return mvc.perform(get(path)).andExpect(status().isOk()).andReturn(); }
    private CalendarOverview overview(MvcResult result) { return (CalendarOverview)result.getModelAndView().getModel().get("overview"); }
    private CalendarOverview.Day day(MvcResult result,String date) { return overview(result).weeks().stream().flatMap(w->w.days().stream()).filter(d->d.source().date().equals(LocalDate.parse(date))).findFirst().orElseThrow(); }
    private String main(MvcResult result)throws Exception { String html=result.getResponse().getContentAsString();return html.substring(html.indexOf("<main"),html.indexOf("</main>")); }
    private Long recurring(String title)throws Exception {
        mvc.perform(post("/recurring").with(csrf()).param("title",title).param("status","IN_PROGRESS").param("recurrenceType","DAILY").param("projectId",project.toString())).andExpect(status().is3xxRedirection());
        return db.queryForObject("SELECT id FROM regular_tasks WHERE title=?",Long.class,title);
    }
    private Long task(String title,String date,int estimate,String priority,String deadline)throws Exception {
        var request=post("/tasks").with(csrf()).param("title",title).param("status","TODO").param("projectId",project.toString())
                .param("plannedDate",date).param("estimateMinutes",Integer.toString(estimate)).param("priority",priority);
        if(deadline!=null)request.param("deadlineAt",deadline);
        mvc.perform(request).andExpect(status().is3xxRedirection());
        return db.queryForObject("SELECT id FROM tasks WHERE title=?",Long.class,title);
    }
}
