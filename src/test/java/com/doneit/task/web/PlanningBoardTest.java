package com.doneit.task.web;

import com.doneit.support.IntegrationTestSupport;
import com.doneit.project.application.ProjectService;
import com.doneit.task.application.*;
import com.doneit.task.application.PlanningBoardService.*;
import com.doneit.task.application.command.CreateTaskCommand;
import com.doneit.task.application.view.*;
import com.doneit.task.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CalendarHistoryTest.FixedClockConfiguration.class)
@WithMockUser(username="owner")
class PlanningBoardTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ProjectService projects;
    @Autowired TaskApplicationService tasks;
    @Autowired ObjectMapper json;
    Long project;
    final LocalDate today=LocalDate.of(2026,4,8);
    final String back="/planning?mode=week&week=2026-04-06&date=2026-04-08";
    @BeforeEach void setup() {
        db.execute("TRUNCATE users CASCADE");
        db.update("INSERT INTO users(login,password_hash,created_at,updated_at) VALUES('owner','hash',NOW(),NOW()),('other','hash',NOW(),NOW())");
        projects.create("Board project","BOARD",null);
        project=db.queryForObject("SELECT id FROM projects WHERE code='BOARD'",Long.class);
    }
    Long seed(String title,TaskStatus status,LocalDate day,LocalTime time,Integer estimate) {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("owner","hash",List.of()));
        return tasks.createTask(new CreateTaskCommand(title,"Keep notes",time==null?null:day.atTime(time),null,project,time==null?day:null,
                status==TaskStatus.BACKLOG,TaskPriority.HIGH,status,estimate,PlanningType.WANTED)).id();
    }
    MvcResult page(String query)throws Exception {return mvc.perform(get("/planning"+query)).andExpect(status().isOk()).andReturn();}
    Object model(MvcResult result,String key){return result.getModelAndView().getModel().get(key);}
    @SuppressWarnings("unchecked") List<Day> days(MvcResult result){return (List<Day>)model(result,"plannerDays");}
    @Test void preparationSeparatesSourcesAndWeekOnlyOffersOrdinaryBacklog()throws Exception {
        seed("Ready",TaskStatus.BACKLOG,null,null,60);seed("New task",TaskStatus.NEW,null,null,30);
        seed("Paused",TaskStatus.PAUSED,null,null,15);
        mvc.perform(post("/recurring").with(csrf()).param("title","Series backlog").param("projectId",project.toString()).param("status","BACKLOG").param("recurrenceType","DAILY"))
                .andExpect(status().is3xxRedirection());
        var prep=page("");
        assertThat(((Queue)model(prep,"plannerBacklog")).cards()).extracting(Card::title).containsExactly("Paused","Ready");
        assertThat(((Queue)model(prep,"plannerInbox")).cards()).extracting(Card::title).containsExactly("New task");
        String html=prep.getResponse().getContentAsString();
        assertThat(html.indexOf("data-group=\"backlog\"")).isLessThan(html.indexOf("data-group=\"inbox\""));
        var week=page("?mode=week");assertThat(model(week,"plannerInbox")).isNull();assertThat(days(week)).hasSize(7);
        assertThat(week.getResponse().getContentAsString()).doesNotContain("Series backlog","New task","data-group=\"inbox\"");
    }
    @Test void incomingRequiresBacklogBeforeSchedulingAndCancelHasNoMutation()throws Exception {
        Long id=seed("Incoming",TaskStatus.NEW,null,null,60);
        mvc.perform(post("/planning/tasks/"+id+"/schedule").with(csrf()).param("plannedDate",today.toString()).param("redirectTo",back))
                .andExpect(status().isConflict());
        assertThat(db.queryForObject("SELECT status FROM tasks WHERE id=?",String.class,id)).isEqualTo("NEW");
        mvc.perform(post("/tasks/"+id).with(csrf()).param("title","Incoming").param("status","TODO").param("plannedDate",today.toString())
                .param("projectId",project.toString()).param("redirectTo",back)).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model().attributeHasFieldErrors("form","status"));
        assertThat(db.queryForObject("SELECT status FROM tasks WHERE id=?",String.class,id)).isEqualTo("NEW");
        mvc.perform(post("/planning/tasks/"+id+"/backlog").with(csrf()).param("redirectTo",back)).andExpect(status().is3xxRedirection());
        mvc.perform(post("/planning/tasks/"+id+"/schedule").with(csrf()).param("plannedDate",today.toString()).param("redirectTo",back))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(back));
        assertThat(db.queryForObject("SELECT status FROM tasks WHERE id=?",String.class,id)).isEqualTo("TODO");
        assertThat(db.queryForObject("SELECT planned_date FROM tasks WHERE id=?",LocalDate.class,id)).isEqualTo(today);
        assertThat(db.queryForObject("SELECT planned_for_at FROM tasks WHERE id=?",LocalDateTime.class,id)).isNull();
    }
    @Test void shiftingTimesClearingTimeAndReturningToBacklogPreserveEstimateAndOtherAttributes()throws Exception {
        Long id=seed("Timed",TaskStatus.TODO,today,LocalTime.of(10,0),120);
        db.update("UPDATE tasks SET end_time=TIME '12:00' WHERE id=?",id);
        mvc.perform(post("/planning/tasks/"+id+"/schedule").with(csrf()).param("plannedDate",today.plusDays(1).toString())
                .param("plannedWithTime","true").param("time","10:00").param("redirectTo",back)).andExpect(status().is3xxRedirection());
        assertThat(db.queryForObject("SELECT planned_for_at FROM tasks WHERE id=?",LocalDateTime.class,id)).isEqualTo(today.plusDays(1).atTime(10,0));
        assertThat(db.queryForObject("SELECT end_time FROM tasks WHERE id=?",LocalTime.class,id)).isEqualTo(LocalTime.NOON);
        mvc.perform(post("/planning/tasks/"+id+"/schedule").with(csrf()).param("plannedDate",today.toString()).param("plannedWithTime","false").param("redirectTo",back)).andExpect(status().is3xxRedirection());
        assertThat(db.queryForObject("SELECT end_time FROM tasks WHERE id=?",LocalTime.class,id)).isNull();
        mvc.perform(post("/planning/tasks/"+id+"/backlog").with(csrf()).param("redirectTo",back)).andExpect(status().is3xxRedirection());
        assertThat(db.queryForObject("SELECT planned_date FROM tasks WHERE id=?",LocalDate.class,id)).isNull();
        assertThat(db.queryForObject("SELECT estimate_minutes FROM tasks WHERE id=?",Integer.class,id)).isEqualTo(120);
        assertThat(db.queryForObject("SELECT description FROM tasks WHERE id=?",String.class,id)).isEqualTo("Keep notes");
        assertThat(db.queryForObject("SELECT project_id FROM tasks WHERE id=?",Long.class,id)).isEqualTo(project);
    }
    @Test void pausedTasksActivateAndFinishedTasksAreNotMovable()throws Exception {
        Long id=seed("Paused",TaskStatus.PAUSED,null,null,20);
        mvc.perform(post("/planning/tasks/"+id+"/schedule").with(csrf()).param("plannedDate",today.toString()).param("redirectTo",back)).andExpect(status().is3xxRedirection());
        assertThat(db.queryForObject("SELECT status FROM tasks WHERE id=?",String.class,id)).isEqualTo("TODO");
        db.update("UPDATE tasks SET status='DONE',completed_at=NOW() WHERE id=?",id);
        mvc.perform(post("/planning/tasks/"+id+"/backlog").with(csrf()).param("redirectTo",back)).andExpect(status().isConflict());
        mvc.perform(post("/planning/tasks/"+id+"/schedule").with(csrf()).param("plannedDate",today.toString()).param("redirectTo",back)).andExpect(status().isConflict());
    }
    @Test void sharedWipRequiresApprovalAndDoesNotPartiallyMoveTask()throws Exception {
        Long existing=seed("Capacity",TaskStatus.TODO,today,null,360);
        db.update("UPDATE tasks SET planning_type='REQUIRED' WHERE id=?",existing);
        Long id=seed("Ready",TaskStatus.BACKLOG,null,null,60);
        db.update("UPDATE users SET wip_enabled=true,wip_limit_minutes=360 WHERE login='owner'");
        var session=new MockHttpSession();
        var request=post("/planning/tasks/"+id+"/schedule").with(csrf()).session(session).header("X-DoneIt-Wip","true")
                .param("plannedDate",today.toString()).param("redirectTo",back);
        var warning=mvc.perform(request).andExpect(status().isConflict()).andExpect(jsonPath("$.days[0].after").value(420)).andReturn();
        assertThat(db.queryForObject("SELECT status FROM tasks WHERE id=?",String.class,id)).isEqualTo("BACKLOG");
        String approval=json.readTree(warning.getResponse().getContentAsString()).get("approval").asText();
        mvc.perform(request.param("wipApproval",approval)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(back));
    }
    @Test void modelGroupsTimedAndUntimedAndUsesCalendarSummaryWithReadOnlyRecurring()throws Exception {
        seed("Untimed",TaskStatus.TODO,today,null,30);
        seed("Later",TaskStatus.TODO,today,LocalTime.of(14,0),null);
        seed("Early",TaskStatus.TODO,today,LocalTime.of(9,0),120);
        mvc.perform(post("/recurring").with(csrf()).param("title","Read-only series").param("status","IN_PROGRESS").param("projectId",project.toString())
                .param("recurrenceType","DAILY").param("occurrenceTime","10:00").param("estimateMinutes","15")).andExpect(status().is3xxRedirection());
        var result=page("?mode=week&date="+today);
        var day=days(result).stream().filter(d->d.summary().source().date().equals(today)).findFirst().orElseThrow();
        assertThat(day.timed()).extracting(Card::title).containsExactly("Early","Read-only series","Later");
        assertThat(day.untimed()).extracting(Card::title).containsExactly("Untimed");
        assertThat(day.timed()).extracting(Card::interval).containsExactly("09:00–11:00","10:00–10:15","14:00");
        assertThat(day.timed().get(1).mutable()).isFalse();assertThat(day.timed().get(1).editHref()).isEmpty();
        assertThat(day.summary().minutes()).isEqualTo(165);assertThat(day.summary().count()).isEqualTo(4);
    }
    @Test void embeddedAndSeparateEditorsReturnToOriginalPlanningContext()throws Exception {
        Long id=seed("Edit me",TaskStatus.BACKLOG,null,null,30);
        String target="/planning?mode=prepare&week=2026-04-06&date=2026-04-08&projectId="+project;
        var embedded=page("?mode=prepare&taskId="+id+"&projectId="+project);
        assertThat(model(embedded,"form")).isInstanceOf(TaskUpsertForm.class);
        assertThat(embedded.getResponse().getContentAsString()).contains("name=\"redirectTo\"","name=\"tagNames\"","data-collapse-sidebar");
        mvc.perform(get("/tasks/"+id+"/edit").param("redirectTo",target)).andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model().attribute("planningReturnTo",target));
        mvc.perform(post("/tasks/"+id).with(csrf()).param("title","Prepared").param("status","BACKLOG").param("planningType","WANTED")
                .param("estimateMinutes","45").param("projectId",project.toString()).param("redirectTo",target)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(target));
        assertThat(db.queryForObject("SELECT estimate_minutes FROM tasks WHERE id=?",Integer.class,id)).isEqualTo(45);
    }
    @Test void queuesArePagedAndProjectAndAccessFiltersAreApplied()throws Exception {
        for(int i=0;i<27;i++)seed("Ready "+i,TaskStatus.BACKLOG,null,null,10);
        var first=(Queue)model(page("?projectId="+project),"plannerBacklog");
        assertThat(first.total()).isEqualTo(27);assertThat(first.cards()).hasSize(25);assertThat(first.pages()).isEqualTo(2);
        assertThat(((Queue)model(page("?backlogPage=2&projectId="+project),"plannerBacklog")).cards()).hasSize(2);
        assertThat((String)model(page("?backlogPage=999&projectId="+project),"plannerBacklogPrevious")).doesNotContain("backlogPage=998");
        mvc.perform(get("/planning?projectId="+project).with(user("other"))).andExpect(status().isNotFound());
        Long id=first.cards().getFirst().id();
        mvc.perform(post("/planning/tasks/"+id+"/schedule").with(user("other")).with(csrf()).param("plannedDate",today.toString()).param("redirectTo",back)).andExpect(status().isNotFound());
        mvc.perform(post("/planning/tasks/"+id+"/schedule").param("plannedDate",today.toString()).param("redirectTo",back)).andExpect(status().isForbidden());
        mvc.perform(post("/planning/tasks/"+id+"/backlog").with(csrf()).param("redirectTo","//example.com")).andExpect(status().isBadRequest());
    }
    @Test void weekNavigationAndDefaultDayAreStableAcrossYears()throws Exception {
        assertThat(model(page("?mode=week"),"plannerDate")).isEqualTo(today);
        assertThat(model(page("?mode=week&week=2027-01-01"),"plannerDate")).isEqualTo(LocalDate.of(2026,12,28));
        var result=page("?mode=week&date=2027-01-01");
        assertThat(model(result,"plannerDate")).isEqualTo(LocalDate.of(2027,1,1));
        assertThat(model(result,"plannerStart")).isEqualTo(LocalDate.of(2026,12,28));
        assertThat((String)model(result,"plannerNextHref")).contains("week=2027-01-04");
        mvc.perform(get("/calendar?month=2026-04")).andExpect(status().isOk()).andExpect(view().name("calendar"));
    }
    @Test void existingCommentsAndAssigneeFormsKeepPlanningReturnContext()throws Exception {
        Long id=seed("Prepare with details",TaskStatus.BACKLOG,null,null,30);
        mvc.perform(post("/tasks/"+id+"/comments").with(csrf()).param("body","Preparation note").param("redirectTo",back))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(back));
        mvc.perform(post("/tasks/"+id+"/assignees").with(csrf()).param("redirectTo",back))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(back));
        var result=page("?taskId="+id);
        assertThat(result.getResponse().getContentAsString()).contains("Preparation note");
    }
}
