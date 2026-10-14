package com.doneit.task.web;

import com.doneit.project.application.ProjectService;
import com.doneit.project.domain.TaskVisibilityMode;
import com.doneit.schedule.application.ScheduleView;
import com.doneit.schedule.application.ScheduleView.*;
import com.doneit.support.IntegrationTestSupport;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CalendarHistoryTest.FixedClockConfiguration.class)
@WithMockUser(username="owner")
class ScheduleControllerTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ProjectService projects;
    Long project,owner,katya;
    @BeforeEach void setUp() {
        db.execute("TRUNCATE TABLE users CASCADE");
        owner=userId("owner");katya=userId("katya");
        projects.create("Shared","SHARED",null);
        project=db.queryForObject("SELECT id FROM projects WHERE code='SHARED'",Long.class);
        projects.add(project,katya);
    }
    @Test void explicitEndRoundTripsAndEstimateModeClearsIt() throws Exception {
        Long id=task("Client","owner","2026-04-08T09:00","10:30",15);
        var row=db.queryForMap("SELECT end_time,estimate_minutes FROM tasks WHERE id=?",id);
        assertThat(row.get("end_time")).isEqualTo(java.sql.Time.valueOf("10:30:00"));
        assertThat(row.get("estimate_minutes")).isEqualTo(90);
        var model=mvc.perform(get("/tasks/{id}/edit",id).with(user("owner")))
                .andExpect(status().isOk()).andReturn().getModelAndView().getModel();
        var form=(TaskUpsertForm)model.get("form");
        assertThat(form.getEndTime()).isEqualTo(LocalTime.of(10,30));
        assertThat(form.isExplicitEndTime()).isTrue();
        mvc.perform(post("/tasks/{id}",id).with(user("owner")).with(csrf())
                .param("title","Client").param("projectId",project.toString()).param("status","IN_PROGRESS")
                .param("plannedWithTime","true").param("plannedForAt","2026-04-08T09:00")
                .param("explicitEndTime","false").param("endTime","11:00").param("estimateMinutes","45"))
                .andExpect(status().is3xxRedirection());
        assertThat(db.queryForObject("SELECT end_time FROM tasks WHERE id=?",LocalTime.class,id)).isNull();
        assertThat(db.queryForObject("SELECT estimate_minutes FROM tasks WHERE id=?",Integer.class,id)).isEqualTo(45);
    }
    @Test void movingTimedTaskPreservesDurationAndDateOnlyMoveClearsEnd() throws Exception {
        Long id=task("Move","owner","2026-04-08T09:00","10:30",15);
        mvc.perform(post("/tasks/{id}/reschedule",id).with(user("owner")).with(csrf())
                .param("plannedForAt","2026-04-09T11:00")).andExpect(status().is3xxRedirection());
        assertThat(db.queryForObject("SELECT end_time FROM tasks WHERE id=?",LocalTime.class,id)).isEqualTo(LocalTime.of(12,30));
        mvc.perform(post("/tasks/{id}/reschedule",id).with(user("owner")).with(csrf())
                .param("plannedDate","2026-04-10")).andExpect(status().is3xxRedirection());
        assertThat(db.queryForObject("SELECT end_time FROM tasks WHERE id=?",LocalTime.class,id)).isNull();
        Long drag=task("Drag","owner","2026-04-08T09:00","10:30",90);
        mvc.perform(post("/kanban/reorder").with(user("owner")).with(csrf()).param("status","BACKLOG")
                .param("taskIds",drag.toString())).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT end_time FROM tasks WHERE id=?",LocalTime.class,drag)).isNull();
    }
    @Test void missingDurationDoesNotLeakPreviousDayIntoUnscheduledList() throws Exception {
        task("No duration","owner","2026-04-08T09:00",null,null);
        assertThat(schedule("2026-04-08","owner").unscheduled()).extracting(Entry::title).containsExactly("No duration");
        assertThat(schedule("2026-04-09","owner").unscheduled()).isEmpty();
    }
    @Test void invalidEndIsAFormErrorAndDateOnlyTasksIgnoreInactiveEnd() throws Exception {
        mvc.perform(post("/tasks").with(user("owner")).with(csrf()).param("title","Invalid")
                .param("projectId",project.toString()).param("status","IN_PROGRESS").param("plannedWithTime","true")
                .param("plannedForAt","2026-04-08T09:00").param("explicitEndTime","true").param("endTime","09:00"))
                .andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT count(*) FROM tasks WHERE title='Invalid'",Integer.class)).isZero();
        mvc.perform(post("/tasks").with(user("owner")).with(csrf()).param("title","Date only")
                .param("projectId",project.toString()).param("status","TODO").param("plannedDate","2026-04-08")
                .param("explicitEndTime","true").param("endTime","10:00")).andExpect(status().is3xxRedirection());
        assertThat(db.queryForObject("SELECT end_time FROM tasks WHERE title='Date only'",LocalTime.class)).isNull();
    }
    @Test void schedulePlacesCurrentUserFirstAndAllocatesPeakOverlapWidth() throws Exception {
        task("Mine","owner","2026-04-08T08:00",null,60);
        task("A","katya","2026-04-08T09:00",null,60);
        task("B","katya","2026-04-08T09:15",null,60);
        task("C","katya","2026-04-08T09:30",null,60);
        Long shared=task("Joint","owner","2026-04-08T10:00",null,60);
        db.update("INSERT INTO task_assignees(task_id,user_id,assigned_at) VALUES(?,?,NOW())",shared,katya);
        task("Undated","owner",null,null,30);
        var view=schedule("2026-04-08","owner");
        assertThat(view.columns()).extracting(c->c.person().login()).containsExactly("owner","katya");
        assertThat(view.columns()).extracting(Column::lanes).containsExactly(1,3);
        assertThat(view.columns().get(1).blocks()).extracting(b->b.entry().title()).containsExactly("A","B","C","Joint");
        assertThat(view.unscheduled()).extracting(Entry::title).contains("Undated");
        assertThat(schedule("2026-04-08","katya").columns().getFirst().person().login()).isEqualTo("katya");
    }
    @Test void visibilityProjectAndPlanningFiltersAreRespected() throws Exception {
        task("Owner only","owner","2026-04-08T09:00",null,60);
        task("Katya private","katya","2026-04-08T10:00",null,60);
        asOwner();projects.update(project,"Shared","SHARED",null,TaskVisibilityMode.AUTHOR_AND_ASSIGNEES);
        assertThat(schedule("2026-04-08","owner").columns().stream().flatMap(c->c.blocks().stream()))
                .extracting(b->b.entry().title()).containsExactly("Owner only");
        var filtered=mvc.perform(get("/schedule").with(user("owner")).param("date","2026-04-08")
                .param("projectId",project.toString()).param("planningType","OPTIONAL")).andExpect(status().isOk()).andReturn();
        var view=(ScheduleView)filtered.getModelAndView().getModel().get("schedule");
        assertThat(view.columns()).allSatisfy(c->assertThat(c.blocks()).isEmpty());
        mvc.perform(get("/schedule").with(user("owner")).param("date","bad")).andExpect(status().isBadRequest());
    }
    @Test void recurringIntervalsAndOvernightTasksAppearOnCorrectDays() throws Exception {
        Long night=task("Night","owner","2026-04-08T23:00","01:00",10);
        mvc.perform(post("/recurring").with(user("owner")).with(csrf()).param("title","Training")
                .param("projectId",project.toString()).param("status","IN_PROGRESS").param("recurrenceType","DAILY")
                .param("occurrenceTime","18:00").param("explicitEndTime","true").param("endTime","19:30"))
                .andExpect(status().is3xxRedirection());
        Long recurring=db.queryForObject("SELECT id FROM regular_tasks WHERE title='Training'",Long.class);
        assertThat(db.queryForObject("SELECT estimate_minutes FROM regular_tasks WHERE id=?",Integer.class,recurring)).isEqualTo(90);
        assertThat(schedule("2026-04-08","owner").columns().getFirst().blocks())
                .anySatisfy(b->{assertThat(b.entry().title()).isEqualTo("Training");assertThat(b.durationMinutes()).isEqualTo(90);});
        var carry=schedule("2026-04-09","owner").columns().getFirst().blocks().stream()
                .filter(b->b.entry().id().equals(night)&&!b.entry().recurring()).findFirst().orElseThrow();
        assertThat(carry.topMinutes()).isZero();assertThat(carry.durationMinutes()).isEqualTo(60);
        mvc.perform(post("/recurring/{id}/skip-today",recurring).with(user("owner")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(schedule("2026-04-08","owner").columns().getFirst().blocks())
                .noneMatch(b->b.entry().recurring());
    }
    private Long task(String title,String login,String start,String end,Integer estimate) throws Exception {
        var request=post("/tasks").with(user(login)).with(csrf()).param("title",title).param("projectId",project.toString()).param("status","IN_PROGRESS");
        if(start!=null)request.param("plannedWithTime","true").param("plannedForAt",start);
        else request.param("withoutPlannedDate","true");
        if(end!=null)request.param("explicitEndTime","true").param("endTime",end);
        if(estimate!=null)request.param("estimateMinutes",estimate.toString());
        mvc.perform(request).andExpect(status().is3xxRedirection());
        return db.queryForObject("SELECT id FROM tasks WHERE title=?",Long.class,title);
    }
    private ScheduleView schedule(String date,String login) throws Exception {
        return (ScheduleView)mvc.perform(get("/schedule").with(user(login)).param("date",date))
                .andExpect(status().isOk()).andReturn().getModelAndView().getModel().get("schedule");
    }
    private Long userId(String login) {
        return db.queryForObject("INSERT INTO users(login,password_hash,created_at,updated_at) VALUES(?,'hash',NOW(),NOW()) RETURNING id",Long.class,login);
    }
    private void asOwner() { SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("owner","pw","ROLE_USER")); }
}
