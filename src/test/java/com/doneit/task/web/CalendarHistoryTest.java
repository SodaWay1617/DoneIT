package com.doneit.task.web;

import com.doneit.recurring.application.RegularTaskService.Occurrence;
import com.doneit.support.IntegrationTestSupport;
import com.doneit.task.application.view.CalendarMonthView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "doneit")
class CalendarHistoryTest extends IntegrationTestSupport {
    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-04-08T09:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate db;
    private Long mainProjectId;

    @BeforeEach
    void setUp() {
        db.execute("TRUNCATE TABLE users CASCADE");
        db.update("""
                INSERT INTO users(login, password_hash, display_name, created_at, updated_at)
                VALUES ('doneit', 'hash', 'DoneIt', TIMESTAMP '2026-04-01 09:00:00', TIMESTAMP '2026-04-01 09:00:00')
                """);
        mainProjectId = db.queryForObject("SELECT id FROM projects WHERE default_project = TRUE", Long.class);
    }

    @Test
    void ordinaryBacklogTaskDoesNotRepeatAcrossCalendarInSameSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/tasks").session(session).with(csrf())
                        .param("title", "Ordinary idea").param("status", "BACKLOG"))
                .andExpect(status().is3xxRedirection());
        Long id = db.queryForObject("SELECT id FROM tasks WHERE title = 'Ordinary idea'", Long.class);
        assertThat(ordinaryDates(calendar(session, null), id)).isEmpty();

        mockMvc.perform(post("/tasks/{id}", id).session(session).with(csrf())
                        .param("title", "Ordinary idea").param("status", "TODO")
                        .param("withoutPlannedDate", "true").param("withoutDeadline", "true"))
                .andExpect(status().is3xxRedirection());
        assertThat(ordinaryDates(calendar(session, null), id)).containsExactly(LocalDate.of(2026, 4, 8));
        MvcResult previousMonth = mockMvc.perform(get("/calendar").session(session).param("month", "2026-02"))
                .andExpect(status().isOk()).andReturn();
        assertThat(ordinaryDates(previousMonth, id)).isEmpty();

        mockMvc.perform(post("/tasks/{id}/reschedule", id).session(session).with(csrf())
                        .param("plannedDate", "2026-04-10"))
                .andExpect(status().is3xxRedirection());
        assertThat(ordinaryDates(calendar(session, null), id)).containsExactly(LocalDate.of(2026, 4, 10));

        mockMvc.perform(post("/tasks/{id}/done", id).session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(ordinaryDates(calendar(session, null), id)).containsExactly(LocalDate.of(2026, 4, 8));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DONE", "CLOSED"})
    void finishedRecurringTaskKeepsHistoryWhenReplacementIsCreatedInAnotherProject(String finalStatus) throws Exception {
        MockHttpSession session = new MockHttpSession();
        Long oldId = createRecurring(mainProjectId, "Old work", "IN_PROGRESS");
        db.update("UPDATE regular_tasks SET activated_at = TIMESTAMP '2026-04-05 09:00:00' WHERE id = ?", oldId);
        mockMvc.perform(post("/recurring/{id}/done-today", oldId).session(session).with(csrf())
                        .param("date", "2026-04-06").param("spentMinutes", "40"))
                .andExpect(status().is3xxRedirection());
        db.update("INSERT INTO regular_task_skips VALUES (?, DATE '2026-04-07', TIMESTAMP '2026-04-07 09:00:00')", oldId);

        mockMvc.perform(post("/recurring/{id}", oldId).session(session).with(csrf())
                        .param("title", "Old work").param("projectId", mainProjectId.toString())
                        .param("status", finalStatus).param("recurrenceType", "DAILY")
                        .param("spentMinutes", "40"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/finished"));

        mockMvc.perform(post("/projects").with(csrf()).param("name", "Work").param("code", "WORK"))
                .andExpect(status().is3xxRedirection());
        Long workId = db.queryForObject("SELECT id FROM projects WHERE name = 'Work'", Long.class);
        Long newId = createRecurring(workId, "New work", "IN_PROGRESS");

        MvcResult result = calendar(session, null);
        List<Occurrence> oldOccurrences = occurrences(result, oldId);
        assertThat(oldOccurrences).extracting(Occurrence::date).containsExactly(
                LocalDate.of(2026, 4, 5), LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 8));
        assertThat(oldOccurrences).filteredOn(Occurrence::completed).extracting(Occurrence::date)
                .containsExactly(LocalDate.of(2026, 4, 6));
        assertThat(occurrences(result, newId)).allMatch(occ -> !occ.date().isBefore(LocalDate.of(2026, 4, 8)));
        assertThat(occurrences(result, newId)).extracting(Occurrence::date).contains(LocalDate.of(2026, 4, 9));
        assertThat(result.getResponse().getContentAsString()).contains("Old work", "New work");
        assertThat(occurrences(calendar(session, mainProjectId), oldId)).hasSize(3);
        assertThat(occurrences(calendar(session, mainProjectId), newId)).isEmpty();
        assertThat(occurrences(calendar(session, workId), oldId)).isEmpty();
        assertThat(db.queryForObject("SELECT SUM(minutes) FROM regular_task_time_entries WHERE regular_task_id = ?", Integer.class, oldId))
                .isEqualTo(40);

        MvcResult list = mockMvc.perform(get("/tasks").param("date", "2026-04-08"))
                .andExpect(status().isOk()).andReturn();
        assertThat(((List<?>) list.getModelAndView().getModel().get("regularActive")))
                .extracting(task -> ((com.doneit.recurring.domain.RegularTask) task).id())
                .containsExactly(newId);
    }

    @Test
    void recurringTaskClosedBeforeActivationHasNoCalendarHistory() throws Exception {
        Long id = createRecurring(mainProjectId, "Unused idea", "BACKLOG");
        mockMvc.perform(post("/recurring/{id}", id).with(csrf())
                        .param("title", "Unused idea").param("projectId", mainProjectId.toString())
                        .param("status", "CLOSED").param("recurrenceType", "DAILY"))
                .andExpect(status().is3xxRedirection());
        assertThat(occurrences(calendar(new MockHttpSession(), null), id)).isEmpty();
    }

    private Long createRecurring(Long projectId, String title, String taskStatus) throws Exception {
        mockMvc.perform(post("/recurring").with(csrf()).param("projectId", projectId.toString())
                        .param("title", title).param("status", taskStatus).param("recurrenceType", "DAILY"))
                .andExpect(status().is3xxRedirection());
        return db.queryForObject("SELECT id FROM regular_tasks WHERE title = ?", Long.class, title);
    }

    private MvcResult calendar(MockHttpSession session, Long projectId) throws Exception {
        var request = get("/calendar").session(session).param("month", "2026-04");
        if (projectId != null) request.param("projectId", projectId.toString());
        return mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    }

    private static List<LocalDate> ordinaryDates(MvcResult result, Long id) {
        CalendarMonthView calendar = (CalendarMonthView) result.getModelAndView().getModel().get("calendar");
        return calendar.days().stream().flatMap(day -> day.items().stream())
                .filter(item -> item.taskId().equals(id)).map(item -> item.date()).toList();
    }

    private static List<Occurrence> occurrences(MvcResult result, Long id) {
        return ((List<?>) result.getModelAndView().getModel().get("regularCalendar")).stream()
                .map(Occurrence.class::cast).filter(occ -> occ.task().id().equals(id)).toList();
    }
}
