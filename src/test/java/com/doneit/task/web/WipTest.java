package com.doneit.task.web;

import com.doneit.support.IntegrationTestSupport;
import com.doneit.project.application.ProjectService;
import com.doneit.task.application.TaskApplicationService;
import com.doneit.task.application.command.CreateTaskCommand;
import com.doneit.task.domain.*;
import com.doneit.user.application.WipSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CalendarHistoryTest.FixedClockConfiguration.class)
@WithMockUser(username="owner")
class WipTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ProjectService projects;
    @Autowired TaskApplicationService tasks;
    @Autowired ObjectMapper json;
    @Autowired WipSettingsService settings;
    Long project;
    final LocalDate day=LocalDate.of(2026,4,8);
    @BeforeEach void setup() {
        db.execute("TRUNCATE users CASCADE");
        db.update("INSERT INTO users(login,password_hash,created_at,updated_at) VALUES('owner','hash',NOW(),NOW()),('other','hash',NOW(),NOW())");
        projects.create("WIP project","WIP",null);
        project=db.queryForObject("SELECT id FROM projects WHERE code='WIP'",Long.class);
        settings.update(true,6,0);
    }
    Long seed(String title,LocalDate date,int minutes,PlanningType category) {
        asOwner();
        return tasks.createTask(new CreateTaskCommand(title,"Description",null,null,project,date,false,TaskPriority.NORMAL,
                TaskStatus.TODO,minutes,category)).id();
    }
    void asOwner() {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("owner","hash",java.util.List.of()));
    }
    MockHttpServletRequestBuilder form(String path,String title,String category,Integer minutes,LocalDate date) {
        var r=post(path).with(csrf()).header("X-DoneIt-Wip","true").param("title",title).param("status","TODO")
                .param("projectId",project.toString()).param("description","Keep description").param("priority","HIGH")
                .param("tagNames","Prime","Work").param("tagsPresent","true");
        if(category!=null)r.param("planningType",category);
        if(minutes!=null)r.param("estimateMinutes",minutes.toString());
        if(date!=null)r.param("plannedDate",date.toString());else r.param("withoutPlannedDate","true");
        return r;
    }
    @ParameterizedTest @ValueSource(strings={"WANTED","OPTIONAL",""})
    void warningDoesNotWriteAndApprovalBindsToData(String type)throws Exception {
        seed("Existing",day,330,PlanningType.REQUIRED);
        MockHttpSession session=new MockHttpSession();
        var result=mvc.perform(form("/tasks","New",type.isEmpty()?null:type,60,day).session(session))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.days[0].before").value(330))
                .andExpect(jsonPath("$.days[0].after").value(390)).andExpect(jsonPath("$.days[0].excess").value(30)).andReturn();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM tasks WHERE title='New'",Integer.class)).isZero();
        String approval=json.readTree(result.getResponse().getContentAsString()).get("approval").asText();
        mvc.perform(form("/tasks","Changed",type.isEmpty()?null:type,60,day).session(session).param("wipApproval",approval))
                .andExpect(status().isConflict());
        mvc.perform(form("/tasks","New",type.isEmpty()?null:type,60,day).session(session).param("wipApproval",approval))
                .andExpect(status().is3xxRedirection());
        assertThat(db.queryForObject("SELECT description FROM tasks WHERE title='New'",String.class)).isEqualTo("Keep description");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM task_tags tt JOIN tasks t ON t.id=tt.task_id WHERE t.title='New'",Integer.class)).isEqualTo(2);
    }
    @Test void requiredDisabledUndatedAndUnestimatedTasksDoNotWarn()throws Exception {
        seed("Existing",day,500,PlanningType.REQUIRED);
        mvc.perform(form("/tasks","Must","REQUIRED",100,day)).andExpect(status().is3xxRedirection());
        mvc.perform(form("/tasks","Undated","WANTED",100,null)).andExpect(status().is3xxRedirection());
        mvc.perform(form("/tasks","No estimate","OPTIONAL",null,day)).andExpect(status().is3xxRedirection());
        asOwner();settings.update(false,6,0);
        mvc.perform(form("/tasks","Disabled","WANTED",100,day)).andExpect(status().is3xxRedirection());
    }
    @Test void belowLimitAndExactlyAtLimitSave()throws Exception {
        seed("Existing",day,300,PlanningType.REQUIRED);
        mvc.perform(form("/tasks","Below","WANTED",30,day)).andExpect(status().is3xxRedirection());
        mvc.perform(form("/tasks","Exact","OPTIONAL",30,day)).andExpect(status().is3xxRedirection());
    }
    @Test void editsSubtractOldEstimateAndOnlyWarnWhenIncreasingOrReleasingRequired()throws Exception {
        seed("Other",day,330,PlanningType.REQUIRED);
        Long id=seed("Edit",day,60,PlanningType.WANTED);
        mvc.perform(form("/tasks/"+id,"Renamed","WANTED",60,day)).andExpect(status().is3xxRedirection());
        mvc.perform(form("/tasks/"+id,"Renamed","WANTED",30,day)).andExpect(status().is3xxRedirection());
        mvc.perform(form("/tasks/"+id,"Renamed","WANTED",45,day)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.days[0].after").value(375)).andExpect(jsonPath("$.days[0].added").value(15));
        mvc.perform(form("/tasks/"+id,"Renamed","REQUIRED",60,day)).andExpect(status().is3xxRedirection());
        mvc.perform(form("/tasks/"+id,"Renamed","OPTIONAL",60,day)).andExpect(status().isConflict());
        mvc.perform(form("/tasks/"+id,"Renamed","OPTIONAL",20,day)).andExpect(status().is3xxRedirection());
    }
    @ParameterizedTest @ValueSource(strings={"WANTED","OPTIONAL"})
    void quickMoveChecksTargetAndPreservesOriginalUntilApproved(String category)throws Exception {
        seed("Target",day,360,PlanningType.REQUIRED);
        Long id=seed("Move",day.minusDays(1),30,PlanningType.valueOf(category));
        var session=new MockHttpSession();
        var request=post("/tasks/"+id+"/reschedule").with(csrf()).session(session).header("X-DoneIt-Wip","true")
                .param("plannedDate",day.toString()).param("plannedWithTime","false").param("redirectTo","/backlog?page=2");
        var response=mvc.perform(request).andExpect(status().isConflict()).andReturn();
        assertThat(db.queryForObject("SELECT planned_date FROM tasks WHERE id=?",LocalDate.class,id)).isEqualTo(day.minusDays(1));
        String token=json.readTree(response.getResponse().getContentAsString()).get("approval").asText();
        mvc.perform(request.param("wipApproval",token)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/backlog?page=2"));
        assertThat(db.queryForObject("SELECT planned_date FROM tasks WHERE id=?",LocalDate.class,id)).isEqualTo(day);
    }
    @Test void startTodayAndBulkOperationsAreAtomicAndHaveSingleWarning()throws Exception {
        seed("Target",day,360,PlanningType.REQUIRED);
        Long id=seed("Incoming",null,30,PlanningType.WANTED);db.update("UPDATE tasks SET status='NEW' WHERE id=?",id);
        mvc.perform(post("/tasks/"+id+"/start-today").with(csrf()).header("X-DoneIt-Wip","true"))
                .andExpect(status().isConflict());
        Long a=seed("Overdue A",day.minusDays(1),30,PlanningType.WANTED);
        Long b=seed("Overdue B",day.minusDays(2),45,PlanningType.WANTED);
        var session=new MockHttpSession();
        var request=post("/tasks/bulk-move-overdue-to-today").with(csrf()).header("X-DoneIt-Wip","true").session(session);
        var response=mvc.perform(request).andExpect(status().isConflict()).andExpect(jsonPath("$.days.length()").value(1))
                .andExpect(jsonPath("$.days[0].after").value(435)).andReturn();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM tasks WHERE id IN (?,?) AND planned_date<?",Integer.class,a,b,day)).isEqualTo(2);
        String approval=json.readTree(response.getResponse().getContentAsString()).get("approval").asText();
        mvc.perform(request.param("wipApproval",approval)).andExpect(status().is3xxRedirection());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM tasks WHERE id IN (?,?) AND planned_date=?",Integer.class,a,b,day)).isEqualTo(2);
    }
    @Test void bulkShiftReportsSeveralTargetDaysAndUsesNetChanges()throws Exception {
        seed("A",day.minusDays(1),400,PlanningType.WANTED);
        seed("B",day,400,PlanningType.WANTED);
        seed("C",day.plusDays(1),360,PlanningType.REQUIRED);
        mvc.perform(post("/tasks/bulk-move-to-tomorrow").with(csrf()).param("date",day.toString()).header("X-DoneIt-Wip","true"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.days.length()").value(1))
                .andExpect(jsonPath("$.days[0].date").value(day.plusDays(1).toString()))
                .andExpect(jsonPath("$.days[0].after").value(760));
    }
    @Test void finishedForeignAndUnassignedSharedTasksDoNotConsumePersonalCapacity()throws Exception {
        Long done=seed("Done",day,1000,PlanningType.REQUIRED);tasks.markTaskAsDone(done);
        Long closed=seed("Closed",day,1000,PlanningType.REQUIRED);tasks.markTaskAsClosed(closed);
        db.update("INSERT INTO projects(name,code,owner_user_id,created_at,updated_at) SELECT 'Other','OTHER',id,NOW(),NOW() FROM users WHERE login='other'");
        Long foreign=seed("Foreign",day,1000,PlanningType.REQUIRED);
        db.update("UPDATE tasks SET user_id=(SELECT id FROM users WHERE login='other'),project_id=(SELECT id FROM projects WHERE code='OTHER') WHERE id=?",foreign);
        Long shared=seed("Visible, assigned to other",day,1000,PlanningType.REQUIRED);
        db.update("INSERT INTO project_members SELECT ?,id,NOW() FROM users WHERE login='other'",project);
        db.update("DELETE FROM task_assignees WHERE task_id=?",shared);
        db.update("INSERT INTO task_assignees(task_id,user_id,assigned_at) SELECT ?,id,NOW() FROM users WHERE login='other'",shared);
        mvc.perform(form("/tasks","Personal","WANTED",360,day)).andExpect(status().is3xxRedirection());
        mvc.perform(post("/tasks/"+foreign+"/reschedule").with(csrf()).header("X-DoneIt-Wip","true").param("plannedDate",day.toString()))
                .andExpect(status().isNotFound());
    }
    @Test void recurringInstancesContributeButTheirOwnChangesAreNotBlocked()throws Exception {
        mvc.perform(post("/recurring").with(csrf()).param("title","Daily").param("status","IN_PROGRESS").param("projectId",project.toString())
                .param("recurrenceType","DAILY").param("estimateMinutes","360").param("planningType","WANTED"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(form("/tasks","Extra","OPTIONAL",10,day)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.days[0].before").value(360));
        Long regular=db.queryForObject("SELECT id FROM regular_tasks",Long.class);
        db.update("INSERT INTO regular_task_completions(regular_task_id,occurrence_date,completed_at) VALUES(?,?,NOW())",regular,day);
        mvc.perform(form("/tasks","After completion","WANTED",360,day)).andExpect(status().is3xxRedirection());
    }
    @Test void serverFallbackCanReturnPopulatedEditorAndInvalidatesChangedLoad()throws Exception {
        seed("Existing",day,360,PlanningType.REQUIRED);
        var session=new MockHttpSession();
        var request=form("/tasks","Fallback","WANTED",30,day).session(session).with(r->{r.removeHeader("X-DoneIt-Wip");return r;});
        var result=mvc.perform(request).andExpect(status().isConflict()).andExpect(view().name("wip-confirm")).andReturn();
        String approval=(String)result.getModelAndView().getModel().get("wipApproval");
        mvc.perform(form("/tasks","Fallback","WANTED",30,day).session(session).param("wipReview","true"))
                .andExpect(status().isOk()).andExpect(view().name("task-form")).andExpect(content().string(org.hamcrest.Matchers.containsString("Keep description")));
        seed("Concurrent new task",day,10,PlanningType.REQUIRED);
        mvc.perform(form("/tasks","Fallback","WANTED",30,day).session(session).param("wipApproval",approval)).andExpect(status().isConflict());
    }
    @Test void settingsValidationOwnershipAndTotalSummary()throws Exception {
        assertThatThrownBy(()->settings.update(true,0,0)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->settings.update(false,-1,0)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        mvc.perform(post("/settings").with(csrf()).param("workloadGreenHours","4").param("workloadYellowHours","8").param("workloadOrangeHours","12")
                .param("wipEnabled","true").param("wipHours","7").param("wipMinutes","15")).andExpect(status().is3xxRedirection());
        asOwner();assertThat(settings.current().minutes()).isEqualTo(435);
        assertThat(db.queryForObject("SELECT wip_enabled FROM users WHERE login='other'",Boolean.class)).isFalse();
        seed("Need",day,30,PlanningType.REQUIRED);seed("Want",day,20,PlanningType.WANTED);seed("Optional",day,10,PlanningType.OPTIONAL);
        var html=mvc.perform(get("/tasks?date="+day)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("today-total\" data-minutes=\"60\"");
        assertThat(html.indexOf("today-total")).isLessThan(html.indexOf("data-planning-type=\"REQUIRED\""));
        mvc.perform(get("/settings")).andExpect(status().isOk());
        mvc.perform(post("/tasks").param("title","No csrf")).andExpect(status().isForbidden());
    }
}
