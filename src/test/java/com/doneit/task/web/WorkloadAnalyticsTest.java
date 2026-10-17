package com.doneit.task.web;

import com.doneit.analytics.application.*;
import com.doneit.project.application.ProjectService;
import com.doneit.project.domain.TaskVisibilityMode;
import com.doneit.support.IntegrationTestSupport;
import com.doneit.task.domain.PlanningType;
import com.doneit.task.domain.TaskRepository;
import com.doneit.task.domain.TaskStatus;
import java.util.List;
import java.time.*;
import org.junit.jupiter.api.*;
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
class WorkloadAnalyticsTest extends IntegrationTestSupport {
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired ProjectService projects;
    @Autowired WorkloadAnalyticsService analytics;
    @Autowired TaskRepository tasks;
    Long owner,katya,project;
    LocalDate today;
    LocalDateTime origin;

    @BeforeEach void setup() {
        db.execute("TRUNCATE users CASCADE");
        today=db.queryForObject("SELECT CURRENT_DATE",LocalDate.class);
        origin=db.queryForObject("SELECT started_at FROM workload_history_origin",LocalDateTime.class);
        db.update("UPDATE workload_history_origin SET started_at=?",today.minusDays(30).atStartOfDay());
        owner=user("owner");katya=user("katya");
        projects.create("Family","FAMILY",null);
        project=db.queryForObject("SELECT id FROM projects WHERE code='FAMILY'",Long.class);
        projects.add(project,katya);
    }
    @AfterEach void restoreOrigin() { db.update("UPDATE workload_history_origin SET started_at=?",origin); }

    @Test void actualWorkUsesEntryDatesIncludingUnfinishedAndCancelledButNotOtherUsers() {
        Long ongoing=task(owner,"Work",60,"IN_PROGRESS",today,"REQUIRED");
        Long cancelled=task(owner,"Cancel",100,"CLOSED",today,"REQUIRED");
        db.update("UPDATE tasks SET closed_at=? WHERE id=?",today.atTime(12,0),cancelled);
        entry(false,ongoing,owner,today,90);entry(false,ongoing,owner,today,-10);
        entry(false,ongoing,owner,today.minusDays(1),500);
        entry(false,ongoing,katya,today,999);
        entry(false,cancelled,owner,today,20);
        var report=analytics.report(today,1,null,null);
        assertThat(report.actualMinutes()).isEqualTo(100);
        assertThat(report.accuracy().completed()).isZero();
        assertThat(report.cancelled()).isEqualTo(1);
        assertThat(report.projects()).hasSize(1);
        assertThat(report.projects().getFirst().minutes()).isEqualTo(100);
        assertThat(report.plannedMinutes()).isEqualTo(60);
    }

    @Test void historicalPlansSurviveMovesAndEstimateChangesWithoutCountingPrematureFuturePlans() {
        Long id=task(owner,"Move",60,"IN_PROGRESS",today.minusDays(1),"REQUIRED");
        normalize(id,false,today.minusDays(2).atTime(9,0));
        db.update("UPDATE tasks SET planned_date=?,estimate_minutes=120,updated_at=NOW() WHERE id=?",today,id);
        db.update("UPDATE task_planning_history SET observed_at=? WHERE task_id=? AND id=(SELECT MAX(id) FROM task_planning_history WHERE task_id=?)",
                today.atTime(9,0),id,id);
        var report=analytics.report(today,2,null,null);
        assertThat(report.days().getFirst().requiredMinutes()).isEqualTo(60);
        assertThat(report.days().getLast().requiredMinutes()).isEqualTo(120);
        assertThat(report.reschedules()).isEqualTo(1);
        assertThat(report.rescheduledTasks()).isEqualTo(1);
        db.update("UPDATE tasks SET planned_date=? WHERE id=?",today.plusDays(3),id);
        db.update("UPDATE tasks SET planned_date=? WHERE id=?",today.plusDays(4),id);
        assertThat(analytics.report(today.plusDays(3),1,null,null).plannedMinutes()).isZero();
        assertThat(analytics.report(today,1,null,null).reschedules()).isEqualTo(3);
        assertThat(analytics.report(today,1,null,null).rescheduledTasks()).isEqualTo(1);
    }

    @Test void loadSeparatesCategoriesAndUsesRequiredOrActualForThresholds() {
        task(owner,"Need",480,"IN_PROGRESS",today,"REQUIRED");
        task(owner,"Want",600,"TODO",today,"WANTED");
        task(owner,"Maybe",600,"TODO",today,"OPTIONAL");
        Long foreign=task(katya,"Foreign",1000,"TODO",today,"REQUIRED");
        var report=analytics.report(today,1,null,null);
        assertThat(report.plannedMinutes()).isEqualTo(1680);
        assertThat(report.days().getFirst().level()).isEqualTo("green");
        assertThat(report.overloadedDays()).isZero();
        entry(false,foreign,owner,today,481);
        assertThat(analytics.report(today,1,null,null).overloadedDays()).isEqualTo(1);
        assertThat(analytics.report(today,1,null,PlanningType.WANTED).plannedMinutes()).isEqualTo(600);
    }

    @Test void recurringPlansFollowIntervalsAndClosedHistorySurvives() {
        Long id=regular(30);
        normalize(id,true,today.minusDays(6).atStartOfDay());
        db.update("UPDATE regular_tasks SET repeat_interval=2,anchor_date=? WHERE id=?",today.minusDays(6),id);
        normalize(id,true,today.minusDays(6).atStartOfDay());
        entry(true,id,owner,today,40);
        db.update("INSERT INTO regular_task_completions VALUES(?,?,NOW())",id,today);
        var report=analytics.report(today,7,null,null);
        assertThat(report.plannedMinutes()).isEqualTo(120);
        assertThat(report.actualMinutes()).isEqualTo(40);
        assertThat(report.accuracy().completed()).isEqualTo(1);
        db.update("UPDATE regular_tasks SET status='DONE',regular_status='INACTIVE',finished_at=? WHERE id=?",today.atTime(12,0),id);
        assertThat(analytics.report(today,7,null,null).plannedMinutes()).isEqualTo(120);
        assertThat(analytics.report(today.plusDays(2),1,null,null).plannedMinutes()).isZero();
    }

    @Test void qualityExcludesMissingValuesAndUsesWeightedError() {
        Long shortTask=task(owner,"Short",10,"DONE",today,"REQUIRED");
        Long longTask=task(owner,"Long",90,"DONE",today,"REQUIRED");
        task(owner,"No fact",100,"DONE",today,"REQUIRED");
        Long unestimated=task(owner,"No estimate",null,"DONE",today,"REQUIRED");
        for(Long id:new Long[]{shortTask,longTask,unestimated})
            db.update("UPDATE tasks SET completed_at=? WHERE id=?",today.atTime(12,0),id);
        entry(false,shortTask,owner,today,20);entry(false,longTask,owner,today,90);
        entry(false,unestimated,owner,today,999);
        var quality=analytics.report(today,1,null,null).accuracy();
        assertThat(quality.compared()).isEqualTo(2);
        assertThat(quality.underestimated()).isEqualTo(1);
        assertThat(quality.underestimatedPercent()).isEqualTo(50);
        assertThat(quality.errorPercent()).isEqualTo(10);
    }

    @Test void assignmentChangesEndOldPlanVersionsAndCurrentVisibilityProtectsHistory() {
        Long id=task(owner,"Assigned",60,"TODO",today,"REQUIRED");
        normalize(id,false,today.minusDays(2).atStartOfDay());
        db.update("DELETE FROM task_assignees WHERE task_id=?",id);
        db.update("INSERT INTO task_assignees(task_id,user_id,assigned_at) VALUES(?,?,NOW())",id,katya);
        db.update("UPDATE task_planning_history SET observed_at=? WHERE task_id=? AND id<>(SELECT MIN(id) FROM task_planning_history WHERE task_id=?)",
                today.minusDays(1).atTime(12,0),id,id);
        assertThat(analytics.report(today,1,null,null).plannedMinutes()).isZero();
        projects.update(project,"Family","FAMILY",null,TaskVisibilityMode.AUTHOR_AND_ASSIGNEES);
        Long hidden=task(katya,"Hidden",1000,"TODO",today,"REQUIRED");
        entry(false,hidden,owner,today,123);
        assertThat(analytics.report(today,1,null,null).actualMinutes()).isZero();
    }

    @Test void unknownHistoricalPlanIsNotPresentedAsMeasuredZeroAndInputsAreValidated() throws Exception {
        db.update("UPDATE workload_history_origin SET started_at=?",today.atStartOfDay());
        var report=analytics.report(today,30,null,null);
        assertThat(report.partialHistory()).isTrue();
        assertThat(report.days().getFirst().planKnown()).isFalse();
        mvc.perform(get("/analytics/workload").param("endDate",today.toString()))
                .andExpect(status().isOk()).andExpect(view().name("workload-analytics"));
        mvc.perform(get("/analytics/workload").param("days","0")).andExpect(status().isBadRequest());
        mvc.perform(get("/analytics/workload").param("days","367")).andExpect(status().isBadRequest());
        mvc.perform(get("/analytics/workload").param("projectId","999999")).andExpect(status().isForbidden());
    }

    @Test void taskDeletionCascadesHistoryAndDoesNotBlockEmptyProjectDeletion() {
        Long id=task(owner,"Delete",60,"TODO",today,"REQUIRED");
        db.update("UPDATE tasks SET planned_date=? WHERE id=?",today.plusDays(1),id);
        regular(30);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM task_planning_history WHERE project_id=?",Long.class,project)).isPositive();
        db.update("DELETE FROM tasks WHERE project_id=?",project);
        db.update("DELETE FROM regular_tasks WHERE project_id=?",project);
        projects.delete(project);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM task_planning_history WHERE project_id=?",Long.class,project)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM task_reschedules WHERE project_id=?",Long.class,project)).isZero();
    }

    @Test void bulkMovesAndKanbanChangesAreRecordedByTheSameHistoryMechanism() {
        Long id=task(owner,"Bulk",60,"TODO",today.minusDays(1),"REQUIRED");
        normalize(id,false,today.minusDays(1).atStartOfDay());
        Long optional=task(owner,"Optional",30,"TODO",today.minusDays(1),"OPTIONAL");
        assertThat(tasks.bulkMoveOverdueOpenDatedTasksToDate(owner,today,LocalDateTime.now())).isEqualTo(1);
        assertThat(analytics.report(today,1,null,null).reschedules()).isEqualTo(1);
        assertThat(tasks.findById(optional).orElseThrow().plannedDate()).isEqualTo(today.minusDays(1));
        tasks.reorderKanban(owner,TaskStatus.DONE,List.of(id),LocalDateTime.now());
        assertThat(analytics.report(today,1,null,null).plannedMinutes()).isEqualTo(60);
        assertThat(analytics.report(today,1,null,null).accuracy().completed()).isEqualTo(1);
    }

    @Test void nonPlanningEditsDoNotCreateRedundantHistory() {
        Long id=task(owner,"Stable",60,"TODO",today,"REQUIRED");
        long before=db.queryForObject("SELECT COUNT(*) FROM task_planning_history WHERE task_id=?",Long.class,id);
        db.update("UPDATE tasks SET title='Renamed',estimate_minutes=60,updated_at=NOW() WHERE id=?",id);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM task_planning_history WHERE task_id=?",Long.class,id)).isEqualTo(before);
    }

    @Test void averagePanelUsesFilteredPlansAndUserThresholds() throws Exception {
        task(owner,"Need",120,"TODO",today,"REQUIRED");
        task(owner,"Want",60,"TODO",today,"WANTED");
        db.update("UPDATE users SET workload_green_minutes=300,workload_yellow_minutes=600,workload_orange_minutes=900 WHERE id=?",owner);
        var averages=analytics.report(today,1,null,null).averages();
        assertThat(averages.scales()).extracting(WorkloadAnalyticsView.Scale::minutes).containsExactly(120L,60L,0L,180L);
        assertThat(analytics.report(today,1,null,PlanningType.WANTED).averages().scales())
                .extracting(WorkloadAnalyticsView.Scale::minutes).containsExactly(0L,60L,0L,60L);
        mvc.perform(get("/analytics/workload").param("days","1").param("endDate",today.toString()))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("role=\"meter\"")));
    }

    @Test void reopeningAnOrdinaryTaskDoesNotEraseItsCancellationHistory() {
        Long id=task(owner,"Reopened",60,"TODO",today,"REQUIRED");
        tasks.markClosed(id,today.atTime(10,0));
        assertThat(analytics.report(today,1,null,null).cancelled()).isEqualTo(1);
        tasks.reorderKanban(owner,TaskStatus.TODO,List.of(id),today.atTime(11,0));
        assertThat(analytics.report(today,1,null,null).cancelled()).isEqualTo(1);
    }

    private Long user(String name) { return db.queryForObject("INSERT INTO users(login,password_hash,created_at,updated_at) VALUES(?,'hash',NOW(),NOW()) RETURNING id",Long.class,name); }
    private Long task(Long author,String title,Integer estimate,String status,LocalDate date,String category) {
        long number=db.queryForObject("UPDATE projects SET next_task_number=next_task_number+1 WHERE id=? RETURNING next_task_number-1",Long.class,project);
        Long id=db.queryForObject("""
                INSERT INTO tasks(user_id,project_id,title,status,estimate_minutes,planned_date,task_number,task_key,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,NOW(),NOW()) RETURNING id
                """,Long.class,author,project,title,status,estimate,date,number,title+"___"+author);
        db.update("UPDATE tasks SET planning_type=? WHERE id=?",category,id);
        return id;
    }
    private Long regular(int estimate) {
        long number=db.queryForObject("UPDATE projects SET next_task_number=next_task_number+1 WHERE id=? RETURNING next_task_number-1",Long.class,project);
        return db.queryForObject("""
                INSERT INTO regular_tasks(user_id,project_id,title,status,estimate_minutes,task_number,task_key,
                    recurrence_type,regular_status,activated_at,created_at,updated_at,status_position)
                VALUES(?,?,'Daily','IN_PROGRESS',?,?,?,'DAILY','ACTIVE',?,NOW(),NOW(),1) RETURNING id
                """,Long.class,owner,project,estimate,number,"Daily___owner",today.minusDays(6).atStartOfDay());
    }
    private void entry(boolean regular,Long id,Long user,LocalDate day,int minutes) {
        db.update("INSERT INTO "+(regular?"regular_task_time_entries":"task_time_entries")+"("
                +(regular?"regular_task_id":"task_id")+",user_id,entry_date,minutes,created_at) VALUES(?,?,?,?,NOW())",id,user,day,minutes);
    }
    private void normalize(Long id,boolean regular,LocalDateTime observed) {
        String col=regular?"regular_task_id":"task_id";
        db.update("DELETE FROM task_planning_history WHERE "+col+"=? AND id<>(SELECT MAX(id) FROM task_planning_history WHERE "+col+"=?)",id,id);
        db.update("UPDATE task_planning_history SET observed_at=? WHERE "+col+"=?",observed,id);
    }
}
