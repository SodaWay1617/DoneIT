package com.doneit.task.web;

import com.doneit.analytics.application.AnalyticsService;
import com.doneit.assignment.application.TaskAssignmentService;
import com.doneit.comment.application.TaskCommentService;
import com.doneit.project.application.ProjectService;
import com.doneit.project.domain.TaskVisibilityMode;
import com.doneit.support.IntegrationTestSupport;
import com.doneit.task.application.SubtaskService;
import com.doneit.task.application.view.CalendarMonthView;
import com.doneit.task.application.view.CalendarDayView;
import com.doneit.task.application.view.PlanningLoad;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CalendarHistoryTest.FixedClockConfiguration.class)
@WithMockUser(username = "owner")
class ProjectTaskVisibilityTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ProjectService projects;
    @Autowired TaskAssignmentService assignments;
    @Autowired TaskCommentService comments;
    @Autowired SubtaskService subtasks;
    @Autowired AnalyticsService analytics;
    Long owner, katya, other, project, mine, delegated, joint, hers, hersRegular, delegatedRegular, jointRegular;

    @BeforeEach
    void setUp() {
        db.execute("TRUNCATE TABLE users CASCADE");
        owner = userId("owner"); katya = userId("katya"); other = userId("other");
        projects.create("Family", "FAMILY", null);
        project = db.queryForObject("SELECT id FROM projects WHERE code='FAMILY'", Long.class);
        projects.add(project, katya); projects.add(project, other);
        mine = ordinary(owner, "Mine", 60, "IN_PROGRESS");
        delegated = ordinary(owner, "Delegated", 90, "IN_PROGRESS");
        joint = ordinary(owner, "Joint", 30, "IN_PROGRESS");
        hers = ordinary(katya, "Katya private", 120, "IN_PROGRESS");
        hersRegular = regular(katya, "Katya recurring", 20);
        delegatedRegular = regular(owner, "Delegated recurring", 15);
        jointRegular = regular(katya, "Joint recurring", 10);
        assignments.assignTask(mine, List.of());
        assignments.assignTask(delegated, List.of(katya));
        assignments.assignTask(joint, List.of(owner, katya));
        assignments.assignRegularTask(delegatedRegular, List.of(katya));
        assignments.assignRegularTask(jointRegular, List.of(owner, katya));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/tasks?date=2026-04-08", "/kanban?date=2026-04-08", "/calendar?month=2026-04"})
    void allMembersSeeAllTasksButReceiveOnlyPersonalWorkload(String path) throws Exception {
        assertThat(html(path, "owner")).contains("Katya private", "Katya recurring", "Delegated");
        assertThat(html(path, "katya")).contains("Mine", "Joint", "Delegated recurring");
        assertThat(load(path, "owner")).isEqualTo(new PlanningLoad(100, 0, 0));
        assertThat(load(path, "katya")).isEqualTo(new PlanningLoad(285, 0, 0));
        assertThat(load(path, "other")).isEqualTo(PlanningLoad.empty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/tasks?date=2026-04-08", "/kanban?date=2026-04-08", "/calendar?month=2026-04", "/inbox", "/backlog", "/finished"})
    void restrictedModeShowsOnlyAuthorAndAssigneeTasksInEveryView(String path) throws Exception {
        restrict();
        if (path.equals("/inbox") || path.equals("/backlog") || path.equals("/finished")) {
            String state = path.equals("/inbox") ? "NEW" : path.equals("/backlog") ? "BACKLOG" : "DONE";
            ordinary(owner, "Owner exclusive collection", 5, state);
            Long shared = ordinary(owner, "Shared collection", 5, state);
            assignments.assignTask(shared, List.of(katya));
            ordinary(katya, "Katya exclusive collection", 5, state);
            assertThat(html(path, "katya")).contains("Shared collection", "Katya exclusive collection").doesNotContain("Owner exclusive collection");
            assertThat(html(path, "owner")).contains("Owner exclusive collection", "Shared collection").doesNotContain("Katya exclusive collection");
        } else {
            assertThat(html(path, "owner")).contains("Mine", "Delegated", "Joint recurring")
                    .doesNotContain("Katya private", "Katya recurring");
            assertThat(html(path, "katya")).contains("Katya private", "Delegated", "Joint")
                    .doesNotContain(">Mine<");
            assertThat(load(path, "owner")).isEqualTo(new PlanningLoad(100, 0, 0));
            assertThat(load(path, "katya")).isEqualTo(new PlanningLoad(285, 0, 0));
        }
        assertThat(html(path, "other")).doesNotContain("Mine", "Delegated", "Joint", "Katya private", "Shared collection");
    }

    @Test
    void directAccessAndMutationsToHiddenTasksAreRejected() throws Exception {
        restrict();
        mvc.perform(get("/tasks/{id}/edit", hers)).andExpect(status().isNotFound());
        mvc.perform(get("/recurring/{id}/edit", hersRegular)).andExpect(status().isNotFound());
        for (String action : List.of("done", "closed", "track-time", "comments", "assignees", "reschedule")) {
            mvc.perform(post("/tasks/{id}/" + action, hers).with(csrf())
                    .param("minutes", "15").param("body", "No access")
                    .param("plannedDate", "2026-04-09").param("userIds", owner.toString()))
                    .andExpect(status().isNotFound());
        }
        for (String action : List.of("done-today", "skip-today", "track-time", "comments", "assignees")) {
            mvc.perform(post("/recurring/{id}/" + action, hersRegular).with(csrf())
                    .param("date", "2026-04-08").param("minutes", "15").param("body", "No access")
                    .param("userIds", owner.toString())).andExpect(status().isNotFound());
        }
        mvc.perform(post("/kanban/reorder").with(csrf()).param("status", "DONE")
                .param("taskIds", hers.toString())).andExpect(status().isNotFound());
        assertThat(db.queryForObject("SELECT status FROM tasks WHERE id=?", String.class, hers)).isEqualTo("IN_PROGRESS");
        asOwner();
        assertThatThrownBy(() -> comments.forTask(hers)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> comments.forRegularTask(hersRegular)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> subtasks.summary(hers)).isInstanceOf(ResponseStatusException.class);
        assertThat(assignments.visibleTaskAssignees()).doesNotContainKey(hers);
        assertThat(assignments.visibleRegularTaskAssignees()).doesNotContainKey(hersRegular);
    }

    @Test
    void authorsAndAssigneesCanManageRestrictedTasksAndMembershipIsRequired() throws Exception {
        restrict();
        mvc.perform(get("/tasks/{id}/edit", delegated).with(user("katya"))).andExpect(status().isOk());
        mvc.perform(post("/tasks/{id}/comments", delegated).with(user("katya")).with(csrf())
                .param("body", "Visible to both")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/tasks/{id}/track-time", delegated).with(user("katya")).with(csrf())
                .param("minutes", "25")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/recurring/{id}/track-time", delegatedRegular).with(user("katya")).with(csrf())
                .param("date", "2026-04-08").param("minutes", "15")).andExpect(status().is3xxRedirection());
        asOwner();
        assertThat(comments.forTask(delegated)).hasSize(1);
        projects.remove(project, katya);
        mvc.perform(get("/tasks/{id}/edit", delegated).with(user("katya"))).andExpect(status().isNotFound());
        mvc.perform(get("/recurring/{id}/edit", delegatedRegular).with(user("katya"))).andExpect(status().isNotFound());
    }

    @Test
    void reassignmentChangesLoadWithoutChangingAuthorVisibility() throws Exception {
        restrict();
        assertThat(load("/", "owner").requiredMinutes()).isEqualTo(100);
        assignments.assignTask(delegated, List.of(owner, katya));
        assertThat(load("/", "owner").requiredMinutes()).isEqualTo(190);
        assignments.assignTask(delegated, List.of());
        assertThat(load("/", "owner").requiredMinutes()).isEqualTo(190);
        assertThat(load("/", "katya").requiredMinutes()).isEqualTo(195);
        mvc.perform(get("/tasks/{id}/edit", delegated).with(user("katya"))).andExpect(status().isNotFound());
        mvc.perform(get("/tasks/{id}/edit", delegated)).andExpect(status().isOk());
    }

    @Test
    void hiddenParentsAndChildrenDoNotLeakThroughSubtaskCardsOrAnalytics() {
        restrict();
        db.update("UPDATE tasks SET parent_task_id=? WHERE id=?", hers, joint);
        db.update("UPDATE tasks SET parent_task_id=? WHERE id=?", mine, hers);
        assertThat(subtasks.summary(mine).tasks()).isEmpty();
        assertThat(subtasks.summary(mine).estimateMinutes()).isZero();
        assertThat(subtasks.parentOf(joint)).isNull();
        db.update("INSERT INTO task_time_entries(task_id,user_id,entry_date,minutes,created_at) VALUES(?,?,DATE '2026-04-08',25,NOW())", joint, owner);
        db.update("INSERT INTO task_time_entries(task_id,user_id,entry_date,minutes,created_at) VALUES(?,?,DATE '2026-04-08',50,NOW())", hers, owner);
        db.update("INSERT INTO task_time_entries(task_id,user_id,entry_date,minutes,created_at) VALUES(?,?,DATE '2026-04-08',100,NOW())", delegated, katya);
        db.update("UPDATE users SET roll_up_subtask_time=TRUE WHERE id=?", owner);
        var report = analytics.month(YearMonth.of(2026, 4));
        assertThat(report.totalMinutes()).isEqualTo(25);
        assertThat(report.topTasks()).extracting(t -> t.title()).containsExactly("Joint");
    }

    @Test
    void projectModeCanBeSelectedAtCreationAndChangedByOwner() throws Exception {
        mvc.perform(post("/projects").with(csrf()).param("name", "Home").param("code", "HOME")
                .param("taskVisibility", "AUTHOR_AND_ASSIGNEES")).andExpect(status().is3xxRedirection());
        assertThat(db.queryForObject("SELECT task_visibility FROM projects WHERE code='HOME'", String.class))
                .isEqualTo("AUTHOR_AND_ASSIGNEES");
        mvc.perform(get("/projects")).andExpect(status().isOk());
        assertThat(html("/projects/" + project, "owner")).contains("name=\"taskVisibility\"");
        mvc.perform(post("/projects/{id}", project).with(csrf()).param("name", "Family")
                .param("code", "FAMILY").param("taskVisibility", "AUTHOR_AND_ASSIGNEES"))
                .andExpect(status().is3xxRedirection());
        asOwner();
        assertThat(projects.get(project).taskVisibility()).isEqualTo(TaskVisibilityMode.AUTHOR_AND_ASSIGNEES);
        assertThat(html("/projects/" + project, "katya")).doesNotContain("name=\"taskVisibility\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/tasks?date=2026-04-08", "/calendar?month=2026-04"})
    void assigneeLabelsUseLoginsAndHideOnlySelfAssignments(String path) throws Exception {
        assignments.assignTask(mine, List.of(owner));
        Long myRegular = regular(owner, "My recurring", 10);
        db.update("UPDATE users SET display_name='Profile name' WHERE id IN (?,?)", owner, katya);
        var result = mvc.perform(get(path).with(user("owner"))).andExpect(status().isOk()).andReturn();
        var model = result.getModelAndView().getModel();
        assertThat(model.get("taskAssignees")).isInstanceOf(java.util.Map.class);
        var labels = (java.util.Map<?, ?>) model.get("taskAssignees");
        var regularLabels = (java.util.Map<?, ?>) model.get("regularTaskAssignees");
        assertThat(labels.get(mine)).isNull();
        assertThat(labels.get(delegated)).isEqualTo(List.of("katya"));
        assertThat(labels.get(joint)).isEqualTo(List.of("katya", "owner"));
        assertThat(regularLabels.get(myRegular)).isNull();
        assertThat(regularLabels.get(delegatedRegular)).isEqualTo(List.of("katya"));
        assertThat(regularLabels.get(jointRegular)).isEqualTo(List.of("katya", "owner"));
        assertThat(result.getResponse().getContentAsString())
                .contains("data-assignees=\"katya\"", "data-assignees=\"katya, owner\"")
                .doesNotContain("data-assignees=\"owner\"", "data-assignees=\"Profile name\"");
        var katyaModel = mvc.perform(get(path).with(user("katya"))).andExpect(status().isOk())
                .andReturn().getModelAndView().getModel();
        var katyaLabels = (java.util.Map<?, ?>) katyaModel.get("taskAssignees");
        assertThat(katyaLabels.get(delegated)).isNull();
        assertThat(katyaLabels.get(hers)).isNull();
        assertThat(katyaLabels.get(mine)).isEqualTo(List.of("owner"));
        assertThat(katyaLabels.get(joint)).isEqualTo(List.of("katya", "owner"));
    }

    @Test
    void taskCardsShowOriginalAuthorRegardlessOfAssigneeOrEditor() throws Exception {
        restrict();
        for (String login : List.of("owner", "katya")) {
            var task = mvc.perform(get("/tasks/{id}/edit", delegated).with(user(login)))
                    .andExpect(status().isOk()).andReturn();
            assertThat(task.getModelAndView().getModel().get("taskAuthorLogin")).isEqualTo("owner");
            assertThat(task.getResponse().getContentAsString()).contains("data-task-author", ">owner</strong>");
            var recurring = mvc.perform(get("/recurring/{id}/edit", delegatedRegular).with(user(login)))
                    .andExpect(status().isOk()).andReturn();
            assertThat(recurring.getModelAndView().getModel().get("taskAuthorLogin")).isEqualTo("owner");
            assertThat(recurring.getResponse().getContentAsString()).contains("data-task-author", ">owner</strong>");
        }
        mvc.perform(post("/tasks/{id}", delegated).with(user("katya")).with(csrf())
                .param("title", "Edited by Katya").param("status", "IN_PROGRESS")
                .param("projectId", project.toString()).param("plannedDate", "2026-04-08"))
                .andExpect(status().is3xxRedirection());
        var edited = mvc.perform(get("/tasks/{id}/edit", delegated).with(user("katya")))
                .andExpect(status().isOk()).andReturn();
        assertThat(edited.getModelAndView().getModel().get("taskAuthorLogin")).isEqualTo("owner");
        assertThat(db.queryForObject("SELECT user_id FROM tasks WHERE id=?", Long.class, delegated)).isEqualTo(owner);
        for (String path : List.of("/tasks/" + delegated, "/recurring/" + delegatedRegular)) {
            var invalid = mvc.perform(post(path).with(user("katya")).with(csrf())
                    .param("title", "").param("projectId", project.toString()))
                    .andExpect(status().isOk()).andReturn();
            assertThat(invalid.getModelAndView().getModel().get("taskAuthorLogin")).isEqualTo("owner");
        }
    }

    @Test
    void pastCalendarDaysUsePersonalTrackedTimeRatherThanEstimates() throws Exception {
        assertThat(calendarDay("", "2026-04-07").actualWorkload()).isTrue();
        assertThat(calendarDay("", "2026-04-07").workload()).isEqualTo(PlanningLoad.empty());
        assertThat(calendarDay("", "2026-04-08").actualWorkload()).isFalse();
        assertThat(calendarDay("", "2026-04-08").workload()).isEqualTo(new PlanningLoad(100, 0, 0));
        db.update("UPDATE tasks SET planning_type='WANTED' WHERE id=?", delegated);
        db.update("UPDATE tasks SET planning_type='OPTIONAL' WHERE id=?", joint);
        db.update("UPDATE regular_tasks SET planning_type='OPTIONAL' WHERE id=?", jointRegular);
        entry(false, mine, owner, "2026-04-07", 25);
        entry(false, mine, owner, "2026-04-07", -5);
        entry(false, delegated, owner, "2026-04-07", 40);
        entry(false, joint, owner, "2026-04-07", 20);
        entry(true, delegatedRegular, owner, "2026-04-07", 15);
        entry(true, jointRegular, owner, "2026-04-07", 10);
        entry(false, joint, katya, "2026-04-07", 999);
        entry(true, jointRegular, katya, "2026-04-07", 999);
        db.update("INSERT INTO regular_task_completions VALUES(?,DATE '2026-04-07',NOW())", jointRegular);
        assertThat(calendarDay("", "2026-04-07").workload()).isEqualTo(new PlanningLoad(35, 40, 30));
        assertThat(calendarDay("", "2026-04-06").workload()).isEqualTo(PlanningLoad.empty());
        assertThat(calendarDay("", "2026-04-08").workload()).isEqualTo(new PlanningLoad(60, 0, 40));
        assertThat(calendarDay("", "2026-04-09").workload()).isEqualTo(new PlanningLoad(0, 0, 10));
        db.update("UPDATE regular_tasks SET status='DONE',regular_status='INACTIVE',finished_at=TIMESTAMP '2026-04-08 10:00:00' WHERE id=?", jointRegular);
        db.update("UPDATE tasks SET status='DONE',completed_at=TIMESTAMP '2026-04-08 10:00:00',planned_date=DATE '2026-04-09' WHERE id=?", mine);
        assignments.assignTask(delegated, List.of(katya));
        assertThat(calendarDay("", "2026-04-07").workload()).isEqualTo(new PlanningLoad(35, 40, 30));
        String html = html("/calendar?month=2026-04", "owner");
        assertThat(html).contains("data-workload-kind=\"actual\"", "data-workload-kind=\"planned\"");
    }

    @Test
    void historicalTrackedTimeRespectsProjectCategoryAndVisibilityFilters() throws Exception {
        entry(false, mine, owner, "2026-04-07", 20);
        db.update("UPDATE tasks SET planning_type='WANTED' WHERE id=?", delegated);
        entry(false, delegated, owner, "2026-04-07", 30);
        Long mainTask = ordinary(owner, "Main task", 15, "IN_PROGRESS");
        Long mainProject = db.queryForObject("SELECT id FROM projects WHERE owner_user_id=? AND default_project", Long.class, owner);
        db.update("UPDATE tasks SET project_id=? WHERE id=?", mainProject, mainTask);
        entry(false, mainTask, owner, "2026-04-07", 70);
        assertThat(calendarDay("", "2026-04-07").workload()).isEqualTo(new PlanningLoad(90, 30, 0));
        assertThat(calendarDay("&projectId=" + project, "2026-04-07").workload()).isEqualTo(new PlanningLoad(20, 30, 0));
        assertThat(calendarDay("&projectId=" + mainProject, "2026-04-07").workload()).isEqualTo(new PlanningLoad(70, 0, 0));
        assertThat(calendarDay("&planningType=WANTED", "2026-04-07").workload()).isEqualTo(new PlanningLoad(0, 30, 0));
        restrict();
        entry(false, hers, owner, "2026-04-07", 500);
        entry(true, hersRegular, owner, "2026-04-07", 500);
        assertThat(calendarDay("", "2026-04-07").workload()).isEqualTo(new PlanningLoad(90, 30, 0));
        assertThat(calendarDay("", "2026-04-07").items()).isEmpty();
    }

    private void entry(boolean regular, Long task, Long person, String date, int minutes) {
        String table = regular ? "regular_task_time_entries" : "task_time_entries";
        String column = regular ? "regular_task_id" : "task_id";
        db.update("INSERT INTO " + table + "(" + column + ",user_id,entry_date,minutes,created_at) VALUES(?,?,?,?,NOW())",
                task, person, LocalDate.parse(date), minutes);
    }

    private CalendarDayView calendarDay(String filters, String date) throws Exception {
        var model = mvc.perform(get("/calendar?month=2026-04" + filters).with(user("owner")))
                .andExpect(status().isOk()).andReturn().getModelAndView().getModel();
        asOwner();
        return ((CalendarMonthView) model.get("calendar")).days().stream()
                .filter(day -> day.date().equals(LocalDate.parse(date))).findFirst().orElseThrow();
    }

    @Test
    void membersCannotChangeProjectModeAndDefaultProjectIsProtected() throws Exception {
        mvc.perform(post("/projects/{id}", project).with(user("katya")).with(csrf())
                .param("name", "Family").param("code", "FAMILY").param("taskVisibility", "AUTHOR_AND_ASSIGNEES"))
                .andExpect(status().isForbidden());
        assertThat(db.queryForObject("SELECT task_visibility FROM projects WHERE id=?", String.class, project))
                .isEqualTo("ALL_MEMBERS");
        Long main = db.queryForObject("SELECT id FROM projects WHERE owner_user_id=? AND default_project", Long.class, owner);
        mvc.perform(post("/projects/{id}", main).with(user("owner")).with(csrf())
                .param("name", "MAIN").param("code", "MAIN").param("taskVisibility", "AUTHOR_AND_ASSIGNEES"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/projects/{id}", project).with(user("owner")).with(csrf())
                .param("name", "Family").param("code", "FAMILY").param("taskVisibility", "INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bulkMovesDoNotRescheduleOtherPeoplesWork() throws Exception {
        db.update("UPDATE tasks SET planned_date=DATE '2026-04-07' WHERE project_id=?", project);
        mvc.perform(post("/tasks/bulk-move-overdue-to-today").with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(db.queryForObject("SELECT planned_date FROM tasks WHERE id=?", LocalDate.class, mine)).isEqualTo(LocalDate.of(2026,4,8));
        assertThat(db.queryForObject("SELECT planned_date FROM tasks WHERE id=?", LocalDate.class, hers)).isEqualTo(LocalDate.of(2026,4,7));
        assertThat(db.queryForObject("SELECT planned_date FROM tasks WHERE id=?", LocalDate.class, delegated)).isEqualTo(LocalDate.of(2026,4,7));
    }

    private void restrict() { projects.update(project, "Family", "FAMILY", null, TaskVisibilityMode.AUTHOR_AND_ASSIGNEES); }
    private Long userId(String login) {
        return db.queryForObject("INSERT INTO users(login,password_hash,display_name,created_at,updated_at) VALUES(?,'hash',?,NOW(),NOW()) RETURNING id", Long.class, login, login);
    }
    private Long ordinary(Long author, String title, int estimate, String status) {
        return db.queryForObject("""
                INSERT INTO tasks(user_id,project_id,title,status,planned_date,estimate_minutes,created_at,updated_at,completed_at,task_number,task_key)
                VALUES(?,?,?,?,DATE '2026-04-08',?,TIMESTAMP '2026-04-01 09:00:00',NOW(),
                    CASE WHEN ?='DONE' THEN TIMESTAMP '2026-04-08 12:00:00' ELSE NULL END,?,?) RETURNING id
                """, Long.class, author, project, title, status, estimate, status, nextNumber(), title + "___" + author);
    }
    private Long regular(Long author, String title, int estimate) {
        return db.queryForObject("""
                INSERT INTO regular_tasks(user_id,project_id,task_number,task_key,title,status,priority,recurrence_type,
                    regular_status,activated_at,created_at,updated_at,estimate_minutes,status_position)
                VALUES(?,?,?,? ,?,'IN_PROGRESS','NORMAL','DAILY','ACTIVE',TIMESTAMP '2026-04-01 09:00:00',NOW(),NOW(),?,1) RETURNING id
                """, Long.class, author, project, nextNumber(), title + "___" + author, title, estimate);
    }
    private Long nextNumber() {
        return db.queryForObject("UPDATE projects SET next_task_number=next_task_number+1 WHERE id=? RETURNING next_task_number-1", Long.class, project);
    }
    private String html(String path, String login) throws Exception {
        try {
            return mvc.perform(get(path).with(user(login))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        } finally { asOwner(); }
    }
    private PlanningLoad load(String path, String login) throws Exception {
        var model = mvc.perform(get(path).with(user(login))).andExpect(status().isOk()).andReturn().getModelAndView().getModel();
        asOwner();
        if (model.get("calendar") instanceof CalendarMonthView calendar)
            return calendar.days().stream().filter(d -> d.date().equals(LocalDate.of(2026,4,8))).findFirst().orElseThrow().workload();
        return (PlanningLoad) model.get("planningLoad");
    }
    private void asOwner() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("owner", "password", "ROLE_USER"));
    }
}
