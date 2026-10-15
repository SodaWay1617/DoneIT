package com.doneit.task.web;

import com.doneit.analytics.application.*;
import com.doneit.project.application.ProjectService;
import com.doneit.project.domain.TaskVisibilityMode;
import com.doneit.support.IntegrationTestSupport;
import com.doneit.task.domain.PlanningType;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CalendarHistoryTest.FixedClockConfiguration.class)
@WithMockUser(username="owner")
class PlanFactAnalyticsTest extends IntegrationTestSupport {
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired ProjectService projects;
    @Autowired PlanFactService analytics;
    Long owner,katya,project;

    @BeforeEach void setUp() {
        db.execute("TRUNCATE TABLE users CASCADE");
        owner=userId("owner");katya=userId("katya");
        projects.create("Family","FAMILY",null);
        project=db.queryForObject("SELECT id FROM projects WHERE code='FAMILY'",Long.class);
        projects.add(project,katya);
    }

    @Test void completedCohortUsesEntirePersonalTaskCostAndExcludesOpenAndCancelledTasks() {
        Long done=task(owner,"Done",600,"DONE","2026-04-08",null);
        entry(false,done,owner,"2026-04-03",600);
        entry(false,done,owner,"2026-04-08",270);
        entry(false,done,owner,"2026-05-01",-30);
        Long open=task(owner,"Open",1000,"IN_PROGRESS",null,null);
        Long closed=task(owner,"Cancelled",1000,"CLOSED","2026-04-08",null);
        entry(false,open,owner,"2026-04-08",900);
        entry(false,closed,owner,"2026-04-08",900);
        var report=analytics.report(AnalyticsPeriod.DAY,LocalDate.of(2026,4,8),null,null);
        assertThat(report.total()).isEqualTo(new TimeComparison(600,840,1,0));
        assertThat(report.total().deviationLabel()).isEqualTo("+40%");
        assertThat(report.tasks()).extracting(PlanFactView.TaskRow::title).containsExactly("Done");
        assertThat(report.days().getFirst().comparison()).isEqualTo(report.total());
        assertThat(analytics.report(AnalyticsPeriod.DAY,LocalDate.of(2026,4,3),null,null).total().completedUnits()).isZero();
        var week=analytics.report(AnalyticsPeriod.WEEK,LocalDate.of(2026,4,8),null,null);
        assertThat(week.start()).isEqualTo(LocalDate.of(2026,4,6));
        assertThat(week.end()).isEqualTo(LocalDate.of(2026,4,13));
        assertThat(week.total()).isEqualTo(report.total());
        assertThat(analytics.report(AnalyticsPeriod.MONTH,LocalDate.of(2026,5,1),null,null).total().completedUnits()).isZero();
    }

    @Test void recurringTasksCompareOnlyCompletedOccurrencesAndRetainHistoryAfterClosure() {
        Long id=regular("Daily",30);
        completion(id,"2026-04-07");completion(id,"2026-04-08");
        entry(true,id,owner,"2026-04-07",20);
        entry(true,id,owner,"2026-04-08",50);
        entry(true,id,owner,"2026-04-08",-5);
        entry(true,id,owner,"2026-04-09",200);
        entry(true,id,katya,"2026-04-08",999);
        var day=analytics.report(AnalyticsPeriod.DAY,LocalDate.of(2026,4,8),null,null);
        assertThat(day.total()).isEqualTo(new TimeComparison(30,45,1,0));
        assertThat(day.total().deviationLabel()).isEqualTo("+50%");
        assertThat(analytics.report(AnalyticsPeriod.WEEK,LocalDate.of(2026,4,8),null,null).total())
                .isEqualTo(new TimeComparison(60,65,2,0));
        db.update("UPDATE regular_tasks SET status='CLOSED',regular_status='INACTIVE',finished_at=TIMESTAMP '2026-04-09 12:00:00' WHERE id=?",id);
        assertThat(analytics.regularTask(id).own()).isEqualTo(new TimeComparison(60,65,2,0));
        assertThat(analytics.report(AnalyticsPeriod.DAY,LocalDate.of(2026,4,9),null,null).total().completedUnits()).isZero();
    }

    @Test void sharedTasksNeverAddOtherPeoplesEstimatesOrTime() {
        Long foreign=task(katya,"Foreign",600,"DONE","2026-04-08",null);
        entry(false,foreign,katya,"2026-04-08",800);
        Long delegated=task(owner,"Delegated",600,"DONE","2026-04-08",null);
        assign(delegated,List.of(katya));
        Long both=task(owner,"Both",60,"DONE","2026-04-08",null);
        assign(both,List.of(owner,katya));
        entry(false,both,owner,"2026-04-08",40);
        entry(false,both,katya,"2026-04-08",1000);
        var report=analytics.report(AnalyticsPeriod.DAY,LocalDate.of(2026,4,8),null,null);
        assertThat(report.total()).isEqualTo(new TimeComparison(60,40,1,0));
        entry(false,delegated,owner,"2026-04-08",10);
        assertThat(analytics.report(AnalyticsPeriod.DAY,LocalDate.of(2026,4,8),null,null).total())
                .isEqualTo(new TimeComparison(60,50,2,1));
    }

    @Test void missingEstimateIsReportedWithoutInventingDeviation() {
        Long id=task(owner,"No estimate",null,"DONE","2026-04-08",null);
        entry(false,id,owner,"2026-04-08",60);
        var total=analytics.report(AnalyticsPeriod.MONTH,LocalDate.of(2026,4,8),null,null).total();
        assertThat(total).isEqualTo(new TimeComparison(0,60,1,1));
        assertThat(total.deviationPercent()).isNull();
        assertThat(total.deviationLabel()).isEqualTo("-");
        assertThat(TimeComparison.empty().deviationPercent()).isNull();
    }

    @Test void doneWithoutTimeEntriesIsNotReportedAsMinusOneHundredPercent() {
        Long id=task(owner,"Not tracked",60,"DONE","2026-04-08",null);
        var comparison=analytics.task(id).own();
        assertThat(comparison).isEqualTo(new TimeComparison(60,0,1,0,1));
        assertThat(comparison.deviationPercent()).isNull();
        entry(false,id,owner,"2026-04-08",10);
        entry(false,id,owner,"2026-04-08",-10);
        assertThat(analytics.task(id).own()).isEqualTo(new TimeComparison(60,0,1,0,0));
        assertThat(analytics.task(id).own().deviationLabel()).isEqualTo("-100%");
    }

    @Test void parentGroupingPreservesTotalsAndSeparatesOwnWorkFromChildren() {
        Long parent=task(owner,"Epic",10,"DONE","2026-04-08",null);
        Long first=task(owner,"First",30,"DONE","2026-04-08",parent);
        Long second=task(owner,"Second",60,"DONE","2026-04-08",parent);
        task(owner,"Not finished",1000,"IN_PROGRESS",null,parent);
        entry(false,parent,owner,"2026-04-03",15);
        entry(false,first,owner,"2026-04-04",45);
        entry(false,second,owner,"2026-04-08",60);
        var separate=analytics.report(AnalyticsPeriod.MONTH,LocalDate.of(2026,4,8),null,null);
        assertThat(separate.tasks()).hasSize(3);
        assertThat(separate.total()).isEqualTo(new TimeComparison(100,120,3,0));
        db.update("UPDATE users SET roll_up_subtask_time=TRUE WHERE id=?",owner);
        var grouped=analytics.report(AnalyticsPeriod.MONTH,LocalDate.of(2026,4,8),null,null);
        assertThat(grouped.tasks()).hasSize(1);
        assertThat(grouped.tasks().getFirst().title()).isEqualTo("Epic");
        assertThat(grouped.total()).isEqualTo(separate.total());
        var card=analytics.task(parent);
        assertThat(card.own()).isEqualTo(new TimeComparison(10,15,1,0));
        assertThat(card.children()).isEqualTo(new TimeComparison(90,105,2,0));
        assertThat(card.total()).isEqualTo(grouped.total());
        db.update("UPDATE tasks SET status='IN_PROGRESS',completed_at=NULL WHERE id=?",parent);
        assertThat(analytics.report(AnalyticsPeriod.MONTH,LocalDate.of(2026,4,8),null,null).tasks().getFirst().comparison())
                .isEqualTo(new TimeComparison(90,105,2,0));
    }

    @Test void filtersAndHiddenParentsDoNotLeakPrivateTasks() throws Exception {
        Long hidden=task(katya,"Private epic",600,"DONE","2026-04-08",null);
        Long child=task(owner,"Visible child",30,"DONE","2026-04-08",hidden);
        db.update("UPDATE tasks SET planning_type='WANTED' WHERE id=?",child);
        entry(false,child,owner,"2026-04-08",45);
        entry(false,hidden,owner,"2026-04-08",1000);
        projects.update(project,"Family","FAMILY",null,TaskVisibilityMode.AUTHOR_AND_ASSIGNEES);
        db.update("UPDATE users SET roll_up_subtask_time=TRUE WHERE id=?",owner);
        var report=analytics.report(AnalyticsPeriod.MONTH,LocalDate.of(2026,4,8),project,PlanningType.WANTED);
        assertThat(report.total()).isEqualTo(new TimeComparison(30,45,1,0));
        assertThat(report.tasks()).extracting(PlanFactView.TaskRow::title).containsExactly("Visible child");
        assertThat(analytics.report(AnalyticsPeriod.MONTH,LocalDate.of(2026,4,8),project,PlanningType.REQUIRED).total().completedUnits()).isZero();
        assertThatThrownBy(()->analytics.task(hidden)).isInstanceOf(ResponseStatusException.class);
        Long privateProject=db.queryForObject("SELECT id FROM projects WHERE default_project AND owner_user_id=?",Long.class,katya);
        mvc.perform(get("/analytics").param("projectId",privateProject.toString())).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings={"DAY","WEEK","MONTH"})
    void viewsRenderPeriodTotalsAndTaskCards(String period) throws Exception {
        Long id=task(owner,"Visible report",600,"DONE","2026-04-08",null);
        entry(false,id,owner,"2026-04-05",840);
        var response=mvc.perform(get("/analytics").with(user("owner")).param("date","2026-04-08")
                .param("period",period).header("Accept-Language","ru")).andExpect(status().isOk()).andReturn();
        var report=(PlanFactView)response.getModelAndView().getModel().get("report");
        assertThat(response.getModelAndView().getModel().get("selectedDate")).isEqualTo(LocalDate.of(2026,4,8));
        assertThat(report.total()).isEqualTo(new TimeComparison(600,840,1,0));
        assertThat(response.getResponse().getContentAsString()).contains("data-planned=\"600\"","data-actual=\"840\"","+40%","Visible report");
        var card=mvc.perform(get("/tasks/{id}/edit",id).with(user("owner"))).andExpect(status().isOk()).andReturn();
        assertThat(card.getModelAndView().getModel().get("taskComparison")).isNotNull();
        assertThat(card.getResponse().getContentAsString()).contains("task-comparison","+40%");
    }

    @Test void legacyMonthLinksAndBadInputsAreHandled() throws Exception {
        mvc.perform(get("/analytics").with(user("owner")).param("month","2026-04")).andExpect(status().isOk());
        mvc.perform(get("/analytics").with(user("owner")).param("period","INVALID")).andExpect(status().isBadRequest());
        mvc.perform(get("/analytics").with(user("owner")).param("date","not-a-date")).andExpect(status().isBadRequest());
        var response=mvc.perform(get("/analytics").with(user("owner")).param("date","2026-04-08")
                .param("userId",katya.toString())).andExpect(status().isOk()).andReturn();
        assertThat(((PlanFactView)response.getModelAndView().getModel().get("report")).login()).isEqualTo("owner");
    }

    @Test void recurringTaskCardShowsCompletedOccurrenceComparison() throws Exception {
        Long id=regular("Recurring card",30);
        completion(id,"2026-04-08");
        entry(true,id,owner,"2026-04-08",45);
        var result=mvc.perform(get("/recurring/{id}/edit",id).with(user("owner")))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("task-comparison","+50%");
    }
    @Test void projectFilterDoesNotMixPersonalAndSharedProjects() {
        Long shared=task(owner,"Shared",60,"DONE","2026-04-08",null);
        Long personal=task(owner,"Personal",30,"DONE","2026-04-08",null);
        Long main=db.queryForObject("SELECT id FROM projects WHERE default_project AND owner_user_id=?",Long.class,owner);
        db.update("UPDATE tasks SET project_id=? WHERE id=?",main,personal);
        entry(false,shared,owner,"2026-04-08",90);
        entry(false,personal,owner,"2026-04-08",20);
        assertThat(analytics.report(AnalyticsPeriod.DAY,LocalDate.of(2026,4,8),project,null).total())
                .isEqualTo(new TimeComparison(60,90,1,0));
        assertThat(analytics.report(AnalyticsPeriod.DAY,LocalDate.of(2026,4,8),main,null).total())
                .isEqualTo(new TimeComparison(30,20,1,0));
        assertThat(analytics.report(AnalyticsPeriod.DAY,LocalDate.of(2026,4,8),null,null).total())
                .isEqualTo(new TimeComparison(90,110,2,0));
    }

    private Long userId(String login) {
        return db.queryForObject("INSERT INTO users(login,password_hash,created_at,updated_at) VALUES(?,'hash',NOW(),NOW()) RETURNING id",Long.class,login);
    }
    private Long task(Long author,String title,Integer estimate,String state,String completed,Long parent) {
        Long number=db.queryForObject("UPDATE projects SET next_task_number=next_task_number+1 WHERE id=? RETURNING next_task_number-1",Long.class,project);
        LocalDateTime finish=completed==null?null:LocalDate.parse(completed).atTime(12,0);
        return db.queryForObject("""
                INSERT INTO tasks(user_id,project_id,title,status,estimate_minutes,planned_date,completed_at,closed_at,
                    task_number,task_key,created_at,updated_at,parent_task_id)
                VALUES(?,?,?,?,?,DATE '2026-04-03',?,?,?,?,TIMESTAMP '2026-04-01 09:00:00',NOW(),?) RETURNING id
                """,Long.class,author,project,title,state,estimate,state.equals("DONE")?finish:null,state.equals("CLOSED")?finish:null,
                number,title+"___"+author,parent);
    }
    private Long regular(String title,Integer estimate) {
        Long number=db.queryForObject("UPDATE projects SET next_task_number=next_task_number+1 WHERE id=? RETURNING next_task_number-1",Long.class,project);
        return db.queryForObject("""
                INSERT INTO regular_tasks(user_id,project_id,title,status,estimate_minutes,task_number,task_key,
                    recurrence_type,regular_status,activated_at,created_at,updated_at,status_position)
                VALUES(?,?,?,'IN_PROGRESS',?,?,?,'DAILY','ACTIVE',TIMESTAMP '2026-04-01 09:00:00',NOW(),NOW(),1) RETURNING id
                """,Long.class,owner,project,title,estimate,number,title+"___owner");
    }
    private void completion(Long id,String date) {
        db.update("INSERT INTO regular_task_completions VALUES(?,?,NOW())",id,LocalDate.parse(date));
    }
    private void entry(boolean recurring,Long id,Long person,String date,int minutes) {
        String table=recurring?"regular_task_time_entries":"task_time_entries";
        String column=recurring?"regular_task_id":"task_id";
        db.update("INSERT INTO "+table+"("+column+",user_id,entry_date,minutes,created_at) VALUES(?,?,?,?,NOW())",
                id,person,LocalDate.parse(date),minutes);
    }
    private void assign(Long id,List<Long> people) {
        db.update("DELETE FROM task_assignees WHERE task_id=?",id);
        for(Long person:people) db.update("INSERT INTO task_assignees(task_id,user_id,assigned_at) VALUES(?,?,NOW())",id,person);
    }
}
