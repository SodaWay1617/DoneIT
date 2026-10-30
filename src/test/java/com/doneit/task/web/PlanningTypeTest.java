package com.doneit.task.web;

import com.doneit.recurring.application.RegularTaskForm;
import com.doneit.support.IntegrationTestSupport;
import com.doneit.task.application.view.CalendarMonthView;
import com.doneit.task.application.view.DailyTasksView;
import com.doneit.task.application.view.PlanningLoad;
import com.doneit.task.domain.PlanningType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CalendarHistoryTest.FixedClockConfiguration.class)
@WithMockUser(username = "doneit")
class PlanningTypeTest extends IntegrationTestSupport {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate db;
    private Long projectId;

    @BeforeEach
    void setUp() {
        db.execute("TRUNCATE TABLE users CASCADE");
        db.update("""
                INSERT INTO users(login, password_hash, display_name, created_at, updated_at)
                VALUES ('doneit','hash','DoneIt',TIMESTAMP '2026-04-01 09:00:00',TIMESTAMP '2026-04-01 09:00:00')
                """);
        projectId = db.queryForObject("SELECT id FROM projects WHERE default_project", Long.class);
    }

    @ParameterizedTest
    @EnumSource(PlanningType.class)
    void planningTypeRoundTripsForOrdinaryAndRecurringTasks(PlanningType type) throws Exception {
        Long taskId = ordinary("Ordinary", type, 60, "2026-04-08");
        Long regularId = recurring("Recurring", type, 20);
        var task = db.queryForMap("SELECT planning_type, priority, status, estimate_minutes, planned_date FROM tasks WHERE id=?", taskId);
        assertThat(task.get("planning_type")).isEqualTo(type.name());
        assertThat(task.get("priority")).isEqualTo("CRITICAL");
        assertThat(task.get("status")).isEqualTo("IN_PROGRESS");
        assertThat(task.get("estimate_minutes")).isEqualTo(60);
        assertThat(task.get("planned_date")).isEqualTo(java.sql.Date.valueOf("2026-04-08"));
        TaskUpsertForm form = (TaskUpsertForm) mvc.perform(get("/tasks/{id}/edit", taskId))
                .andExpect(status().isOk()).andReturn().getModelAndView().getModel().get("form");
        assertThat(form.getPlanningType()).isEqualTo(type);
        RegularTaskForm regularForm = (RegularTaskForm) mvc.perform(get("/recurring/{id}/edit", regularId))
                .andExpect(status().isOk()).andReturn().getModelAndView().getModel().get("form");
        assertThat(regularForm.getPlanningType()).isEqualTo(type);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/tasks?date=2026-04-08", "/kanban?date=2026-04-08", "/calendar?month=2026-04"})
    void filterAndVisualCategoriesWorkAcrossViews(String path) throws Exception {
        ordinary("Required task", PlanningType.REQUIRED, 60, "2026-04-08");
        ordinary("Wanted task", PlanningType.WANTED, 30, "2026-04-08");
        ordinary("Optional task", PlanningType.OPTIONAL, 45, "2026-04-08");
        recurring("Required recurring", PlanningType.REQUIRED, 10);
        recurring("Wanted recurring", PlanningType.WANTED, 20);
        recurring("Optional recurring", PlanningType.OPTIONAL, 30);
        String all = mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(all).contains("planning-required", "planning-wanted", "planning-optional", "/planning.css");
        String filtered = mvc.perform(get(path).param("planningType", "WANTED").param("projectId", projectId.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(filtered).contains("Wanted task", "Wanted recurring", "planning-wanted");
        assertThat(filtered).doesNotContain("Required task", "Optional task", "Required recurring", "Optional recurring");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/tasks?date=2026-04-08", "/kanban?date=2026-04-08", "/calendar?month=2026-04", "/backlog", "/inbox", "/finished"})
    void projectManagementAndSettingsRemainAccessibleInCommonNavigation(String path) throws Exception {
        String html = mvc.perform(get(path).param("planningType", "WANTED").param("projectId", projectId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("class=\"app-navigation\"", "class=\"app-project-heading\"",
                "href=\"/projects\"", "href=\"/settings\"");
        if (path.equals("/inbox") || path.equals("/backlog") || path.equals("/finished")) assertThat(html).doesNotContain("name=\"planningType\"", "class=\"view-filters\"");
        else assertThat(html).contains("name=\"projectId\"", "name=\"planningType\"");
    }

    @Test
    void todayHeaderUsesRealLoadAndKeepsActionsAfterTheTaskList() throws Exception {
        ordinary("Required summary", PlanningType.REQUIRED, 60, "2026-04-08");
        ordinary("Wanted summary", PlanningType.WANTED, 30, "2026-04-08");
        recurring("Optional summary", PlanningType.OPTIONAL, 30);
        String html = mvc.perform(get("/").param("lang", "en"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String summary = html.substring(html.indexOf("<section class=\"today-summary\""), html.indexOf("<aside class=\"today-onboarding\""));
        assertThat(summary).contains("--required-end:50.0%;--wanted-end:75.0%;",
                "data-minutes=\"60\"", "data-minutes=\"30\"", "Required: 1 h; Wanted: 30 min; Optional: 30 min");
        assertThat(summary).doesNotContain("is-empty");
        assertThat(html.indexOf("class=\"today-header\"")).isLessThan(html.indexOf("class=\"today-summary\""));
        assertThat(html.indexOf("class=\"today-summary\"")).isLessThan(html.indexOf("class=\"today-onboarding\""));
        assertThat(html.indexOf("id=\"active-tasks\"")).isLessThan(html.indexOf("class=\"today-tools day-tools\""));
        String header = html.substring(html.indexOf("<header class=\"today-header\""), html.indexOf("<section class=\"today-summary\""));
        assertThat(header).contains("Wednesday", "href=\"/tasks/new\"").doesNotContain("view-tabs", "Signed in");
        assertThat(header).doesNotContain("<select name=\"projectId\"");
    }

    @Test
    void emptySummaryAndSelectedDateAreLocalizedAndKeepFilterContext() throws Exception {
        for (String language : List.of("en", "ru")) {
            var result = mvc.perform(get("/tasks").param("date", "2026-04-08").param("lang", language)
                            .param("projectId", projectId.toString()).param("planningType", "WANTED"))
                    .andExpect(status().isOk()).andReturn();
            assertThat(result.getModelAndView().getModel().get("selectedDayIsToday")).isEqualTo(true);
            String html = result.getResponse().getContentAsString();
            assertThat(html).contains("today-ring is-empty", "--required-end:0%;--wanted-end:0%;",
                    language.equals("ru") ? "0 ч" : "0 h",
                    "date=2026-04-07&amp;projectId=" + projectId + "&amp;planningType=WANTED",
                    "date=2026-04-09&amp;projectId=" + projectId + "&amp;planningType=WANTED");
        }
        var result = mvc.perform(get("/tasks").param("date", "2026-04-09").param("lang", "en"))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getModelAndView().getModel().get("selectedDayIsToday")).isEqualTo(false);
        assertThat(result.getResponse().getContentAsString()).contains("Selected date view", "Thursday");
    }

    @Test
    void remainingLoadUsesOnlyScheduledActiveTasksAndOccurrences() throws Exception {
        Long required = ordinary("Required", PlanningType.REQUIRED, 60, "2026-04-08");
        ordinary("Wanted", PlanningType.WANTED, 30, "2026-04-08");
        ordinary("Optional", PlanningType.OPTIONAL, 45, "2026-04-08");
        ordinary("Old work", PlanningType.REQUIRED, 999, "2026-04-07");
        ordinary("No date", PlanningType.WANTED, 500, null);
        ordinary("No estimate", PlanningType.OPTIONAL, null, "2026-04-08");
        Long done = ordinary("Already done", PlanningType.REQUIRED, 80, "2026-04-08");
        mvc.perform(post("/tasks/{id}/done", done).with(csrf())).andExpect(status().is3xxRedirection());
        recurring("Required recurring", PlanningType.REQUIRED, 10);
        Long wantedRegular = recurring("Wanted recurring", PlanningType.WANTED, 20);
        Long optionalRegular = recurring("Optional recurring", PlanningType.OPTIONAL, 30);
        assertThat(load("/")).isEqualTo(new PlanningLoad(70, 50, 75));
        assertThat(load("/kanban?date=2026-04-08")).isEqualTo(new PlanningLoad(70, 50, 75));
        assertThat(load("/?planningType=WANTED")).isEqualTo(new PlanningLoad(0, 50, 0));
        assertThat(calendarLoad("2026-04-08")).isEqualTo(new PlanningLoad(70, 50, 75));
        assertThat(calendarLoad("2026-04-09")).isEqualTo(new PlanningLoad(10, 20, 30));

        mvc.perform(post("/recurring/{id}/done-today", wantedRegular).with(csrf()).param("date", "2026-04-08"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/recurring/{id}/skip-today", optionalRegular).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/tasks/{id}/done", required).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(load("/")).isEqualTo(new PlanningLoad(10, 30, 45));
        assertThat(calendarLoad("2026-04-08")).isEqualTo(new PlanningLoad(10, 30, 45));
        assertThat(calendarLoad("2026-04-09")).isEqualTo(new PlanningLoad(10, 20, 30));
    }

    @Test
    void changingCategoryImmediatelyUpdatesLoadAndPreservesIndependentFields() throws Exception {
        Long id = ordinary("Change category", PlanningType.REQUIRED, 60, "2026-04-08");
        assertThat(load("/")).isEqualTo(new PlanningLoad(60, 0, 0));
        mvc.perform(post("/tasks/{id}", id).with(csrf()).param("title", "Change category")
                        .param("status", "IN_PROGRESS").param("priority", "CRITICAL").param("estimateMinutes", "60")
                        .param("plannedDate", "2026-04-08").param("planningType", "WANTED"))
                .andExpect(status().is3xxRedirection());
        assertThat(load("/")).isEqualTo(new PlanningLoad(0, 60, 0));
        assertThat(calendarLoad("2026-04-08")).isEqualTo(new PlanningLoad(0, 60, 0));
        assertThat(db.queryForObject("SELECT priority FROM tasks WHERE id=?", String.class, id)).isEqualTo("CRITICAL");

        Long regular = recurring("Change recurring", PlanningType.REQUIRED, 20);
        mvc.perform(post("/recurring/{id}", regular).with(csrf()).param("title", "Change recurring")
                        .param("projectId", projectId.toString()).param("status", "IN_PROGRESS")
                        .param("recurrenceType", "DAILY").param("estimateMinutes", "20").param("planningType", "OPTIONAL"))
                .andExpect(status().is3xxRedirection());
        assertThat(load("/")).isEqualTo(new PlanningLoad(0, 60, 20));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/tasks/bulk-move-to-tomorrow", "/tasks/bulk-move-overdue-to-today"})
    void bulkMoveSkipsOptionalAndManualMovePreservesCategory(String endpoint) throws Exception {
        Long required = ordinary("Required move", PlanningType.REQUIRED, 60, "2026-04-07");
        Long wanted = ordinary("Wanted move", PlanningType.WANTED, 30, "2026-04-07");
        Long optional = ordinary("Optional move", PlanningType.OPTIONAL, 45, "2026-04-07");
        mvc.perform(post(endpoint).with(csrf()).param("date", "2026-04-08")).andExpect(status().is3xxRedirection());
        assertThat(db.queryForObject("SELECT planned_date FROM tasks WHERE id=?", LocalDate.class, required)).isEqualTo(LocalDate.of(2026,4,8));
        assertThat(db.queryForObject("SELECT planned_date FROM tasks WHERE id=?", LocalDate.class, wanted)).isEqualTo(LocalDate.of(2026,4,8));
        assertThat(db.queryForObject("SELECT planned_date FROM tasks WHERE id=?", LocalDate.class, optional)).isEqualTo(LocalDate.of(2026,4,7));
        mvc.perform(post("/tasks/{id}/reschedule", optional).with(csrf()).param("plannedDate", "2026-04-10"))
                .andExpect(status().is3xxRedirection());
        assertThat(db.queryForObject("SELECT planning_type FROM tasks WHERE id=?", String.class, optional)).isEqualTo("OPTIONAL");
    }

    @Test
    void defaultAndDatabaseConstraintsProtectCompatibility() throws Exception {
        mvc.perform(post("/tasks").with(csrf()).param("title", "Legacy task").param("status", "TODO")
                        .param("plannedDate", "2026-04-08")).andExpect(status().is3xxRedirection());
        assertThat(db.queryForObject("SELECT planning_type FROM tasks WHERE title='Legacy task'", String.class)).isEqualTo("REQUIRED");
        db.update("UPDATE tasks SET planning_type=DEFAULT");
        assertThat(db.queryForObject("SELECT planning_type FROM tasks WHERE title='Legacy task'", String.class)).isEqualTo("REQUIRED");
        assertThatThrownBy(() -> db.update("UPDATE tasks SET planning_type='INVALID'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        mvc.perform(get("/?planningType=INVALID")).andExpect(status().isBadRequest());
    }

    @Test
    void overdueDeadlineDoesNotDependOnPlanningType() throws Exception {
        for (PlanningType type : PlanningType.values()) {
            Long id = ordinary("Deadline " + type, type, 20, "2026-04-08");
            db.update("UPDATE tasks SET deadline_at=TIMESTAMP '2026-04-07 12:00:00' WHERE id=?", id);
        }
        DailyTasksView daily = (DailyTasksView) mvc.perform(get("/")).andExpect(status().isOk())
                .andReturn().getModelAndView().getModel().get("daily");
        assertThat(daily.activeTasks()).hasSize(3).allMatch(task -> task.overdue());
        CalendarMonthView calendar = (CalendarMonthView) mvc.perform(get("/calendar?month=2026-04")).andExpect(status().isOk())
                .andReturn().getModelAndView().getModel().get("calendar");
        assertThat(calendar.days().stream().filter(day -> day.date().equals(LocalDate.of(2026,4,7))).findFirst().orElseThrow().items())
                .hasSize(3).allMatch(item -> item.deadlineHighlighted());
    }

    private Long ordinary(String title, PlanningType type, Integer estimate, String date) throws Exception {
        var request = post("/tasks").with(csrf()).param("title", title).param("projectId", projectId.toString())
                .param("status", "IN_PROGRESS").param("planningType", type.name()).param("priority", "CRITICAL");
        if (estimate != null) request.param("estimateMinutes", estimate.toString());
        if (date != null) request.param("plannedDate", date);
        mvc.perform(request).andExpect(status().is3xxRedirection());
        return db.queryForObject("SELECT id FROM tasks WHERE title=?", Long.class, title);
    }

    private Long recurring(String title, PlanningType type, Integer estimate) throws Exception {
        var request = post("/recurring").with(csrf()).param("title", title).param("projectId", projectId.toString())
                .param("status", "IN_PROGRESS").param("recurrenceType", "DAILY").param("planningType", type.name());
        if (estimate != null) request.param("estimateMinutes", estimate.toString());
        mvc.perform(request).andExpect(status().is3xxRedirection());
        return db.queryForObject("SELECT id FROM regular_tasks WHERE title=?", Long.class, title);
    }

    private PlanningLoad load(String path) throws Exception {
        return (PlanningLoad) mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getModelAndView().getModel().get("planningLoad");
    }

    private PlanningLoad calendarLoad(String date) throws Exception {
        MvcResult result = mvc.perform(get("/calendar?month=2026-04")).andExpect(status().isOk()).andReturn();
        CalendarMonthView calendar = (CalendarMonthView) result.getModelAndView().getModel().get("calendar");
        return calendar.days().stream().filter(day -> day.date().equals(LocalDate.parse(date))).findFirst().orElseThrow().workload();
    }
}
