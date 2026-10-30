package com.doneit.task.web;

import com.doneit.navigation.AppNavigationService;
import com.doneit.project.application.ProjectService;
import com.doneit.support.IntegrationTestSupport;
import com.doneit.tag.application.TagService;
import com.doneit.task.application.FinishedPageService;
import com.doneit.task.infrastructure.persistence.FinishedTaskRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CalendarHistoryTest.FixedClockConfiguration.class)
@WithMockUser(username = "owner")
class FinishedTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ProjectService projects;
    @Autowired TagService tags;
    Long owner, other, project;

    @BeforeEach void setup() {
        db.execute("TRUNCATE users CASCADE");
        owner = user("owner"); other = user("other");
        projects.create("Family", "FAMILY", null);
        project = db.queryForObject("SELECT id FROM projects WHERE code='FAMILY'", Long.class);
    }

    @Test void historyUsesTerminalDatesAndBoundedRowsWithoutActionControls() throws Exception {
        Long done = ordinary(owner, project, "Finished ordinary", "DONE", "2026-04-07T19:42", 90);
        Long closed = ordinary(owner, project, "Closed ordinary", "CLOSED", "2026-04-08T18:10", null);
        Long missing = ordinary(owner, project, "Unknown finish", "DONE", null, null);
        Long regular = regular("Finished series", "DONE", "2026-04-06T12:00", 20);
        tags.replace(done, false, List.of("Prime"));
        tags.replace(regular, true, List.of("Daily"));
        db.update("UPDATE tasks SET priority='HIGH',description='Hidden verbose description',updated_at=TIMESTAMP '2099-01-01' WHERE id=?", done);
        entry(done, false, 35); entry(regular, true, 12);
        var result = load("/finished?lang=en"); var page = page(result);
        assertThat(page.rows()).extracting(FinishedTaskRepository.Row::title)
                .containsExactly("Closed ordinary", "Finished ordinary", "Finished series", "Unknown finish");
        assertThat(page.rows().get(1).finishedAt()).isEqualTo(LocalDateTime.parse("2026-04-07T19:42"));
        assertThat(page.rows().get(1).actualMinutes()).isEqualTo(35);
        assertThat(page.rows().get(2).recurring()).isTrue();
        String html = result.getResponse().getContentAsString();
        String main = html.substring(html.indexOf("<main"), html.indexOf("</main>"));
        assertThat(main).contains("07.04.2026, 19:42", "08.04.2026, 18:10", "Date not specified", "Estimate: 1h 30m", "Actual: 35 min", "#Prime", "Family", "High")
                .doesNotContain("2099", "Hidden verbose description", "data-row-menu", "/done", "/closed", "view-filters", "inbox-desktop-create");
        assertThat(html).doesNotContain("task-context-menu.js", "time-tracking.js", "class=\"app-icon-button inbox-create\"");
        assertThat(main.split("<h1", -1)).hasSize(2);
        assertThat(result.getModelAndView().getModel()).doesNotContainKeys("regularFinished", "taskCollection", "taskTags");
    }

    @Test void paginationCombinesOrdinaryAndRecurringBeforeLimitAndUsesStableSort() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 53; i++) ids.add(ordinary(owner, project, "History " + i, i % 2 == 0 ? "DONE" : "CLOSED", "2026-04-07T10:00", null));
        regular("Newest series", "CLOSED", "2026-04-08T10:00", null);
        ordinary(owner, project, "Undated last", "DONE", null, null);
        var first = page(load("/finished")); var second = page(load("/finished?page=2")); var last = page(load("/finished?page=999"));
        assertThat(first.total()).isEqualTo(55); assertThat(first.pages()).isEqualTo(3);
        assertThat(first.rows()).hasSize(25); assertThat(second.rows()).hasSize(25); assertThat(last.rows()).hasSize(5);
        assertThat(first.rows().getFirst().title()).isEqualTo("Newest series");
        assertThat(first.rows().get(1).id()).isEqualTo(ids.getLast());
        assertThat(first.rows().getLast().id()).isGreaterThan(second.rows().getFirst().id());
        assertThat(last.number()).isEqualTo(3); assertThat(last.rows().getLast().title()).isEqualTo("Undated last");
        assertThat(second.from()).isEqualTo(26); assertThat(second.to()).isEqualTo(50);
        assertThat(page(load("/finished?size=50")).rows()).hasSize(50);
        assertThat(page(load("/finished?size=100")).rows()).hasSize(55);
        assertThat(page(load("/finished?page=9223372036854775807")).number()).isEqualTo(3);
    }

    @Test void statusCountersRespectProjectAndPersonalTagsWithOrSemantics() throws Exception {
        Long one = ordinary(owner, project, "Prime done", "DONE", "2026-04-07T10:00", null);
        Long two = ordinary(owner, project, "Work closed", "CLOSED", "2026-04-07T10:00", null);
        ordinary(owner, project, "Untagged", "DONE", null, null);
        Long series = regular("Prime series", "CLOSED", "2026-04-06T10:00", null);
        tags.replace(one, false, List.of("Prime", "Work")); tags.replace(two, false, List.of("Work")); tags.replace(series, true, List.of("Prime"));
        Long prime = tags.list().stream().filter(t -> t.name().equals("Prime")).findFirst().orElseThrow().id();
        Long work = tags.list().stream().filter(t -> t.name().equals("Work")).findFirst().orElseThrow().id();
        String query = "/finished?projectId=" + project + "&tagId=" + prime + "&tagId=" + work;
        var all = page(load(query)); var closed = page(load(query + "&status=CLOSED"));
        assertThat(all.total()).isEqualTo(3); assertThat(closed.total()).isEqualTo(2);
        assertThat(closed.counts().total()).isEqualTo(3); assertThat(closed.counts().done()).isEqualTo(1);
        assertThat(all.filter().href(2)).contains("projectId=" + project, "tagId=" + prime, "tagId=" + work, "page=2");
        assertThat(all.filter().statusHref("DONE")).contains("status=DONE").doesNotContain("page=");
        var nav = (AppNavigationService.Navigation) load(query + "&status=CLOSED&size=50&page=5").getModelAndView().getModel().get("appNav");
        assertThat(nav.projects()).allSatisfy(p -> assertThat(p.href()).startsWith("/finished?").contains("status=CLOSED", "size=50", "tagId=").doesNotContain("page="));
    }

    @Test void foreignProjectsTagsAndScheduleOnlyTasksAreNotExposed() throws Exception {
        db.update("INSERT INTO project_members(project_id,user_id,joined_at) VALUES(?,?,NOW())", project, other);
        db.update("UPDATE projects SET task_visibility='SCHEDULE_ONLY' WHERE id=?", project);
        ordinary(other, project, "Hidden finished", "DONE", "2026-04-08T10:00", null);
        Long visible = ordinary(owner, project, "Own finished", "DONE", "2026-04-08T10:00", null);
        tags.replace(visible, false, List.of("Personal"));
        assertThat(page(load("/finished")).total()).isEqualTo(1);
        assertThat(load("/finished").getResponse().getContentAsString()).doesNotContain("Hidden finished");
        Long otherProject = db.queryForObject("SELECT id FROM projects WHERE owner_user_id=? AND default_project", Long.class, other);
        Long otherTag = db.queryForObject("INSERT INTO tags(user_id,name,name_key) VALUES(?,'Secret','secret') RETURNING id", Long.class, other);
        mvc.perform(get("/finished").param("projectId", otherProject.toString())).andExpect(status().isNotFound());
        mvc.perform(get("/finished").param("tagId", otherTag.toString())).andExpect(status().isNotFound());
    }

    @Test void timeEntriesAreSummedAndZeroCorrectionsAreNotMissingData() throws Exception {
        Long zero = ordinary(owner, project, "Corrected time", "DONE", null, null);
        Long absent = ordinary(owner, project, "No time", "CLOSED", null, null);
        entry(zero, false, 20); entry(zero, false, -20);
        var rows = page(load("/finished")).rows();
        assertThat(rows.stream().filter(r -> r.id().equals(zero)).findFirst().orElseThrow().actualMinutes()).isZero();
        assertThat(rows.stream().filter(r -> r.id().equals(absent)).findFirst().orElseThrow().actualMinutes()).isNull();
    }

    @Test void emptyInvalidAndEditorReturnStatesWork() throws Exception {
        assertThat(page(load("/finished?page=999")).number()).isEqualTo(1);
        assertThat(load("/finished?lang=en").getResponse().getContentAsString()).contains("No completed tasks yet.");
        assertThat(load("/finished?status=CLOSED&lang=en").getResponse().getContentAsString()).contains("No tasks match the selected filters.", "Reset filters");
        for (String query : List.of("size=10", "size=0", "page=0", "page=-1", "status=TODO", "status=invalid", "tagId=abc"))
            mvc.perform(get("/finished?" + query)).andExpect(status().isBadRequest());
        Long id = ordinary(owner, project, "Edit history", "DONE", "2026-04-07T10:00", null);
        String target = "/finished?projectId=" + project + "&status=DONE&size=50";
        String html = mvc.perform(get("/tasks/{id}/edit", id).param("redirectTo", target)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("name=\"redirectTo\"", "status=DONE&amp;size=50");
        mvc.perform(post("/tasks/{id}", id).with(csrf()).param("title", "Updated history").param("projectId", project.toString())
                .param("status", "DONE").param("redirectTo", target)).andExpect(redirectedUrl(target));
        assertThat(db.queryForObject("SELECT status FROM tasks WHERE id=?", String.class, id)).isEqualTo("DONE");
        assertThat(load("/finished?lang=ru").getResponse().getContentAsString()).contains("Завершённые").doesNotContain("Выполненные и закрытые");
    }

    private MvcResult load(String path) throws Exception { return mvc.perform(get(path)).andExpect(status().isOk()).andReturn(); }
    private FinishedPageService.Page page(MvcResult result) { return (FinishedPageService.Page) result.getModelAndView().getModel().get("finished"); }
    private Long user(String name) { return db.queryForObject("INSERT INTO users(login,password_hash,created_at,updated_at) VALUES(?,'hash',NOW(),NOW()) RETURNING id", Long.class, name); }
    private Long ordinary(Long author, Long projectId, String title, String state, String date, Integer estimate) {
        Long number = db.queryForObject("UPDATE projects SET next_task_number=next_task_number+1 WHERE id=? RETURNING next_task_number-1", Long.class, projectId);
        return db.queryForObject("INSERT INTO tasks(user_id,project_id,title,status,task_number,task_key,estimate_minutes,completed_at,closed_at,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,NOW(),NOW()) RETURNING id",
                Long.class, author, projectId, title, state, number, "history-" + projectId + "-" + number, estimate,
                state.equals("DONE") && date != null ? LocalDateTime.parse(date) : null, state.equals("CLOSED") && date != null ? LocalDateTime.parse(date) : null);
    }
    private Long regular(String title, String state, String date, Integer estimate) {
        Long number = db.queryForObject("UPDATE projects SET next_task_number=next_task_number+1 WHERE id=? RETURNING next_task_number-1", Long.class, project);
        return db.queryForObject("INSERT INTO regular_tasks(user_id,project_id,title,status,task_number,task_key,estimate_minutes,recurrence_type,regular_status,finished_at,created_at,updated_at,status_position) VALUES(?,?,?,?,?,?,?,'DAILY','INACTIVE',?,NOW(),NOW(),1) RETURNING id",
                Long.class, owner, project, title, state, number, "regular-history-" + number, estimate, date == null ? null : LocalDateTime.parse(date));
    }
    private void entry(Long id, boolean regular, int minutes) {
        db.update("INSERT INTO " + (regular ? "regular_task_time_entries" : "task_time_entries") + "("
                + (regular ? "regular_task_id" : "task_id") + ",user_id,entry_date,minutes,created_at) VALUES(?,?,DATE '2026-04-07',?,NOW())", id, owner, minutes);
    }
}
