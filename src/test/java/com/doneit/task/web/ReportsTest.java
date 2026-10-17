package com.doneit.task.web;

import com.doneit.analytics.application.*;
import com.doneit.project.application.ProjectService;
import com.doneit.project.domain.TaskVisibilityMode;
import com.doneit.support.IntegrationTestSupport;
import com.doneit.task.domain.PlanningType;
import java.time.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username="owner")
class ReportsTest extends IntegrationTestSupport {
    @Autowired JdbcTemplate db;
    @Autowired ProjectService projects;
    @Autowired ReportsService reports;
    @Autowired MockMvc mvc;
    Long owner,katya,project;
    LocalDate today;
    @BeforeEach void setup() {
        db.execute("TRUNCATE users CASCADE");
        owner=user("owner");katya=user("katya");
        today=db.queryForObject("SELECT CURRENT_DATE",LocalDate.class);
        projects.create("Repair","REPAIR",null);
        project=db.queryForObject("SELECT id FROM projects WHERE code='REPAIR'",Long.class);
        projects.add(project,katya);
    }
    @Test void periodSeparatesRecordedPlansPersonalEntriesAndFullCostOfCompletedTasks() {
        Long done=task(owner,"Done",60,"IN_PROGRESS",null);
        finish(done,"DONE");
        Long open=task(owner,"Open",120,"IN_PROGRESS",null);
        entry(false,done,owner,today.minusDays(1),90);
        entry(false,done,owner,today,30);
        entry(false,open,owner,today,40);
        entry(false,open,katya,today,999);
        var report=reports.period(AnalyticsPeriod.DAY,today,null,null);
        assertThat(report.workload().plannedMinutes()).isEqualTo(180);
        assertThat(report.workload().actualMinutes()).isEqualTo(70);
        assertThat(report.completed().total()).isEqualTo(new TimeComparison(60,120,1,0));
        assertThat(report.workload().tasks()).hasSize(2);
        assertThat(report.workload().tasks().stream().mapToLong(WorkloadAnalyticsView.Task::plannedMinutes).sum()).isEqualTo(180);
        assertThat(report.workload().tasks().stream().mapToLong(WorkloadAnalyticsView.Task::actualMinutes).sum()).isEqualTo(70);
    }
    @Test void projectSummarizesOrdinaryInventoryAndKeepsRecurringInstancesSeparate() {
        Long done=task(owner,"Done",60,"DONE",null);finish(done,"DONE");
        Long open=task(owner,"Open",120,"BACKLOG",null);
        Long closed=task(owner,"Cancel",400,"CLOSED",null);finish(closed,"CLOSED");
        entry(false,done,owner,today.minusDays(10),120);
        entry(false,open,owner,today,30);entry(false,closed,owner,today,10);
        Long regular=regular(5);
        completion(regular,today.minusDays(1));completion(regular,today);
        entry(true,regular,owner,today.minusDays(1),20);entry(true,regular,owner,today,20);
        var report=reports.project(project,null);
        assertThat(report.estimateMinutes()).isEqualTo(180);
        assertThat(report.actualMinutes()).isEqualTo(160);
        assertThat(report.completed()).isEqualTo(1);
        assertThat(report.open()).isEqualTo(1);
        assertThat(report.closed()).isEqualTo(1);
        assertThat(report.regularActualMinutes()).isEqualTo(40);
        assertThat(report.regularCompleted()).isEqualTo(2);
        assertThat(report.recurringTasks().getFirst().estimateMinutes()).isEqualTo(5);
    }
    @Test void visibilityIsNotResponsibilityAndOtherUsersTimeNeverCounts() {
        Long foreign=task(katya,"Foreign",1000,"TODO",null);
        Long delegated=task(owner,"Delegated",1000,"TODO",null);
        db.update("DELETE FROM task_assignees WHERE task_id=?",delegated);
        assign(delegated,katya);
        Long both=task(owner,"Both",60,"TODO",null);assign(both,katya);
        entry(false,foreign,owner,today,10);entry(false,foreign,katya,today,1000);
        entry(false,both,owner,today,20);entry(false,both,katya,today,1000);
        var report=reports.project(project,null);
        assertThat(report.estimateMinutes()).isEqualTo(60);
        assertThat(report.actualMinutes()).isEqualTo(30);
        assertThat(report.open()).isEqualTo(1);
        assertThat(report.tasks()).extracting(ReportsService.ProjectTask::title).containsExactly("Both","Foreign");
        projects.update(project,"Repair","REPAIR",null,TaskVisibilityMode.AUTHOR_AND_ASSIGNEES);
        assertThat(reports.project(project,null).actualMinutes()).isEqualTo(20);
        assertThat(reports.period(AnalyticsPeriod.DAY,today,project,null).workload().tasks())
                .extracting(WorkloadAnalyticsView.Task::title).containsExactly("Both","Delegated");
    }
    @Test void parentGroupingNeverDuplicatesInventoryEstimatesOrTrackedTime() {
        Long parent=task(owner,"Epic",20,"DONE",null);finish(parent,"DONE");
        Long child=task(owner,"Child",60,"DONE",parent);finish(child,"DONE");
        entry(false,parent,owner,today,10);entry(false,child,owner,today,30);
        db.update("UPDATE users SET roll_up_subtask_time=TRUE WHERE id=?",owner);
        var projectReport=reports.project(project,null);
        assertThat(projectReport.estimateMinutes()).isEqualTo(80);
        assertThat(projectReport.actualMinutes()).isEqualTo(40);
        assertThat(projectReport.completed()).isEqualTo(2);
        assertThat(reports.period(AnalyticsPeriod.DAY,today,project,null).completed().tasks()).hasSize(1);
        assertThat(reports.period(AnalyticsPeriod.DAY,today,project,null).workload().tasks()).hasSize(2);
    }
    @Test void projectAndPlanningFiltersApplyToEveryReportSection() {
        Long wanted=task(owner,"Want",60,"TODO",null);
        db.update("UPDATE tasks SET planning_type='WANTED' WHERE id=?",wanted);
        entry(false,wanted,owner,today,30);
        var report=reports.period(AnalyticsPeriod.DAY,today,project,PlanningType.WANTED);
        assertThat(report.workload().plannedMinutes()).isEqualTo(60);
        assertThat(report.workload().actualMinutes()).isEqualTo(30);
        assertThat(reports.project(project,PlanningType.REQUIRED).tasks()).isEmpty();
        assertThat(reports.project(project,PlanningType.WANTED).estimateMinutes()).isEqualTo(60);
    }
    @Test void calendarWeekAndMonthHaveExactBoundariesIncludingYearAndLeapMonth() {
        var week=reports.period(AnalyticsPeriod.WEEK,LocalDate.of(2027,1,1),null,null);
        assertThat(week.workload().start()).isEqualTo(LocalDate.of(2026,12,28));
        assertThat(week.workload().end()).isEqualTo(LocalDate.of(2027,1,3));
        var month=reports.period(AnalyticsPeriod.MONTH,LocalDate.of(2028,2,10),null,null);
        assertThat(month.workload().days()).hasSize(29);
        assertThat(month.workload().end()).isEqualTo(LocalDate.of(2028,2,29));
    }
    @ParameterizedTest @EnumSource(ReportType.class)
    void allReportViewsRenderAndLinkToTasks(ReportType type) throws Exception {
        Long id=task(owner,"Visible task",60,"TODO",null);entry(false,id,owner,today,20);
        mvc.perform(get("/reports").param("type",type.name()).param("date",today.toString()).param("projectId",project.toString()))
                .andExpect(status().isOk()).andExpect(view().name("reports"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Visible task")));
    }
    @Test void emptyReportsAndInvalidParametersDoNotLeakAnotherUsersData() throws Exception {
        assertThat(reports.project(project,null).tasks()).isEmpty();
        mvc.perform(get("/reports")).andExpect(status().isOk());
        mvc.perform(get("/reports").param("type","PROJECT")).andExpect(status().isOk());
        mvc.perform(get("/reports").param("type","UNKNOWN")).andExpect(status().isBadRequest());
        mvc.perform(get("/reports").param("date","yesterday")).andExpect(status().isBadRequest());
        mvc.perform(get("/reports").param("type","PROJECT").param("projectId","999999")).andExpect(status().isForbidden());
        mvc.perform(get("/reports").param("projectId","999999")).andExpect(status().isForbidden());
    }
    private Long user(String login) {return db.queryForObject("INSERT INTO users(login,password_hash,created_at,updated_at) VALUES(?,'hash',NOW(),NOW()) RETURNING id",Long.class,login);}
    private long number() {return db.queryForObject("UPDATE projects SET next_task_number=next_task_number+1 WHERE id=? RETURNING next_task_number-1",Long.class,project);}
    private Long task(Long author,String title,Integer estimate,String status,Long parent) {
        long number=number();
        return db.queryForObject("""
                INSERT INTO tasks(user_id,project_id,title,status,estimate_minutes,planned_date,parent_task_id,task_number,task_key,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,NOW(),NOW()) RETURNING id
                """,Long.class,author,project,title,status,estimate,status.equals("BACKLOG")?null:today,parent,number,title+"___"+author);
    }
    private void finish(Long id,String status) {
        db.update("UPDATE tasks SET status=?,completed_at=?,closed_at=? WHERE id=?",status,
                status.equals("DONE")?today.atTime(12,0):null,status.equals("CLOSED")?today.atTime(12,0):null,id);
    }
    private Long regular(int estimate) {
        return db.queryForObject("""
                INSERT INTO regular_tasks(user_id,project_id,title,status,estimate_minutes,task_number,task_key,
                    recurrence_type,regular_status,activated_at,created_at,updated_at,status_position)
                VALUES(?,?,'Daily','IN_PROGRESS',?,?,?,'DAILY','ACTIVE',?,NOW(),NOW(),1) RETURNING id
                """,Long.class,owner,project,estimate,number(),"Daily___owner",today.minusDays(10).atStartOfDay());
    }
    private void completion(Long id,LocalDate day) {db.update("INSERT INTO regular_task_completions VALUES(?,?,NOW())",id,day);}
    private void entry(boolean regular,Long id,Long user,LocalDate day,int minutes) {
        db.update("INSERT INTO "+(regular?"regular_task_time_entries":"task_time_entries")+"("
                +(regular?"regular_task_id":"task_id")+",user_id,entry_date,minutes,created_at) VALUES(?,?,?,?,NOW())",id,user,day,minutes);
    }
    private void assign(Long id,Long person) {db.update("INSERT INTO task_assignees(task_id,user_id,assigned_at) VALUES(?,?,NOW())",id,person);}
}
