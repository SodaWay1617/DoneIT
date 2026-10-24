package com.doneit.task.web;

import com.doneit.project.application.ProjectService;
import com.doneit.task.application.TaskApplicationService;
import com.doneit.task.application.view.DailyTasksView;
import com.doneit.support.IntegrationTestSupport;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CalendarHistoryTest.FixedClockConfiguration.class)
@WithMockUser(username="owner")
class DaySidebarTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ProjectService projects;
    @Autowired TaskApplicationService tasks;
    Long project, prime;

    @BeforeEach void setUp() throws Exception {
        db.execute("TRUNCATE TABLE users CASCADE");
        db.update("INSERT INTO users(login,password_hash,created_at,updated_at) VALUES('owner','hash',NOW(),NOW()),('partner','hash',NOW(),NOW())");
        projects.create("Health","HEALTH",null);
        project=db.queryForObject("SELECT id FROM projects WHERE code='HEALTH'",Long.class);
        Long partner=db.queryForObject("SELECT id FROM users WHERE login='partner'",Long.class);
        projects.add(project,partner);
        create("Late","2026-04-08T18:00","REQUIRED","CRITICAL");
        create("Early","2026-04-08T09:00","REQUIRED","LOW");
        create("Overdue","2026-04-07T08:00","REQUIRED","HIGH");
        create("Optional","2026-04-08T10:00","OPTIONAL","NORMAL");
        mvc.perform(post("/recurring").with(csrf()).param("projectId",project.toString()).param("title","Recurring")
                .param("status","IN_PROGRESS").param("recurrenceType","DAILY").param("occurrenceTime","08:30")
                .param("estimateMinutes","30").param("tagNames","Prime")).andExpect(status().is3xxRedirection());
        prime=db.queryForObject("SELECT id FROM tags WHERE name='Prime'",Long.class);
        asOwner();
    }

    @Test void monthPagingDoesNotChangeSelectedDateTasksOrFilters() throws Exception {
        String base="/tasks?date=2026-04-08&projectId="+project+"&planningType=REQUIRED&tagId="+prime;
        var before=model(base);
        var after=model(base+"&navMonth=2026-12");
        assertThat(((DailyTasksView)after.get("daily")).selectedDate()).isEqualTo(LocalDate.of(2026,4,8));
        assertThat(after.get("compactRows")).isEqualTo(before.get("compactRows"));
        var calendar=(DaySidebarWebConfiguration.Month)after.get("miniCalendar");
        assertThat(calendar.nextHref()).contains("date=2026-04-08","projectId="+project,"planningType=REQUIRED","tagId="+prime,"navMonth=2027-01");
        assertThat(calendar.days()).allSatisfy(day->assertThat(day.href()).contains("/tasks?","projectId="+project,"planningType=REQUIRED","tagId="+prime));
        assertThat(calendar.days().getFirst().date().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(calendar.days().getLast().date().getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(after.get("compactReturnTo").toString()).contains("navMonth=2026-12");
        assertThat(db.queryForObject("SELECT planned_for_at FROM tasks WHERE title='Overdue'",LocalDateTime.class))
                .isEqualTo(LocalDateTime.of(2026,4,7,8,0));
    }

    @Test void leapYearAndSelectedTodayAreHandledByExistingCalendarGrid() throws Exception {
        var leap=(DaySidebarWebConfiguration.Month)model("/tasks?date=2028-02-29").get("miniCalendar");
        assertThat(leap.days()).anySatisfy(day->{assertThat(day.date()).isEqualTo(LocalDate.of(2028,2,29));assertThat(day.selected()).isTrue();});
        var current=(DaySidebarWebConfiguration.Month)model("/").get("miniCalendar");
        assertThat(current.days()).anySatisfy(day->{assertThat(day.date()).isEqualTo(LocalDate.of(2026,4,8));assertThat(day.selected()).isTrue();assertThat(day.today()).isTrue();});
        mvc.perform(get("/?navMonth=invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/?navMonth=9999-12")).andExpect(status().isBadRequest());
    }

    @Test void scheduleIsChronologicalAndExcludesOverdueStartFromAnotherDay() throws Exception {
        var rows=schedule(model("/?projectId="+project));
        assertThat(rows).extracting(CompactTaskRow::title).containsExactly("Recurring","Early","Optional","Late");
        assertThat(rows.getFirst().interval()).isEqualTo("08:30–09:00");
        assertThat(schedule(model("/?projectId="+project+"&planningType=REQUIRED&tagId="+prime)))
                .extracting(CompactTaskRow::title).containsExactly("Recurring","Early","Late");
        db.update("UPDATE tasks SET planned_for_at=NULL,planned_date=DATE '2026-04-08' WHERE project_id=?",project);
        db.update("UPDATE regular_tasks SET occurrence_time=NULL WHERE project_id=?",project);
        String html=mvc.perform(get("/?lang=en")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("No tasks with a start time on this day.");
    }

    @Test void previewsUseSamePersonalGlobalScopeAsActualBulkMoves() throws Exception {
        create("Future","2026-04-09T10:00","REQUIRED","NORMAL");
        Long main=db.queryForObject("SELECT p.id FROM projects p JOIN users u ON u.id=p.owner_user_id WHERE u.login='owner' AND p.default_project",Long.class);
        db.update("UPDATE tasks SET project_id=? WHERE title='Late'",main);
        db.update("UPDATE tasks SET user_id=(SELECT id FROM users WHERE login='partner') WHERE title='Early'");
        db.update("DELETE FROM task_assignees WHERE task_id=(SELECT id FROM tasks WHERE title='Early')");
        // Selected project/tags are deliberately NOT the scope of the existing bulk operation.
        asOwner();
        var preview=tasks.previewBulkMoves(LocalDate.of(2026,4,8));
        assertThat(preview.shiftCount()).isEqualTo(2);
        assertThat(preview.overdueCount()).isEqualTo(1);
        assertThat(tasks.bulkMoveUnfinishedTasksToTomorrow(LocalDate.of(2026,4,8))).isEqualTo(preview.shiftCount());
        assertThat(db.queryForObject("SELECT planned_for_at FROM tasks WHERE title='Overdue'",LocalDateTime.class))
                .isEqualTo(LocalDateTime.of(2026,4,8,8,0));
        assertThat(db.queryForObject("SELECT planned_for_at FROM tasks WHERE title='Future'",LocalDateTime.class))
                .isEqualTo(LocalDateTime.of(2026,4,9,10,0));
        assertThat(db.queryForObject("SELECT planned_for_at FROM tasks WHERE title='Optional'",LocalDateTime.class))
                .isEqualTo(LocalDateTime.of(2026,4,8,10,0));
    }

    @Test void overduePreviewUsesRealTodayEvenWhenHistoricalOrFutureDayIsOpen() throws Exception {
        asOwner();
        assertThat(tasks.previewBulkMoves(LocalDate.of(2026,4,6)).shiftCount()).isZero();
        var future=tasks.previewBulkMoves(LocalDate.of(2026,4,10));
        assertThat(future.today()).isEqualTo(LocalDate.of(2026,4,8));
        assertThat(future.overdueCount()).isEqualTo(1);
        assertThat(tasks.bulkMoveOverdueTasksToToday()).isEqualTo(future.overdueCount());
    }

    @Test void previewEndpointIsReadOnlyNotCachedAndBoundToCurrentUser() throws Exception {
        mvc.perform(get("/api/tasks/bulk-preview?date=2026-04-08")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.username").value("owner")).andExpect(jsonPath("$.shiftCount").value(3))
                .andExpect(jsonPath("$.overdueCount").value(1));
        mvc.perform(get("/api/tasks/bulk-preview?date=2026-04-08").with(user("partner")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("partner"))
                .andExpect(jsonPath("$.shiftCount").value(0));
        mvc.perform(get("/api/tasks/bulk-preview?date=bad")).andExpect(status().isBadRequest());
    }

    private void create(String title,String date,String planning,String priority) throws Exception {
        mvc.perform(post("/tasks").with(csrf()).param("projectId",project.toString()).param("title",title)
                .param("status","IN_PROGRESS").param("plannedWithTime","true").param("plannedForAt",date)
                .param("planningType",planning).param("priority",priority).param("tagNames","Prime"))
                .andExpect(status().is3xxRedirection());
    }
    private Map<String,Object> model(String path) throws Exception {
        try{return mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getModelAndView().getModel();}
        finally{asOwner();}
    }
    @SuppressWarnings("unchecked") private List<CompactTaskRow> schedule(Map<String,Object> model) {
        return (List<CompactTaskRow>)model.get("daySchedule");
    }
    private void asOwner() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("owner","password","ROLE_USER"));
    }
}
