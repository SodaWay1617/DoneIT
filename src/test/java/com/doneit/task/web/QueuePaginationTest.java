package com.doneit.task.web;

import com.doneit.navigation.AppNavigationService;
import com.doneit.project.application.ProjectService;
import com.doneit.support.IntegrationTestSupport;
import com.doneit.tag.application.TagService;
import com.doneit.task.application.BacklogPageService;
import com.doneit.task.application.InboxPageService;
import com.doneit.task.application.view.TaskListPaging;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
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
class QueuePaginationTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ProjectService projects;
    @Autowired TagService tags;
    Long owner, other, project;

    @BeforeEach void setup() {
        db.execute("TRUNCATE users CASCADE");
        owner = user("owner"); other = user("other");
        projects.create("Queue project", "QUEUE", null);
        project = db.queryForObject("SELECT id FROM projects WHERE code='QUEUE'", Long.class);
    }

    @Test void inboxUsesSqlPagesAndPreservesPriorityOrderAndTags() throws Exception {
        for (int i = 0; i < 55; i++) ordinary(owner, "Incoming " + i, "NEW");
        Long urgent = ordinary(owner, "Important incoming", "NEW");
        db.update("UPDATE tasks SET priority='HIGH',updated_at=TIMESTAMP '2020-01-01' WHERE id=?", urgent);
        tags.replace(urgent, false, List.of("Prime"));
        regular("Not an incoming occurrence", "NEW");
        var first = load("/inbox?projectId=" + project); var second = load("/inbox?projectId=" + project + "&page=2");
        assertThat(paging(first).total()).isEqualTo(56); assertThat(inbox(first)).hasSize(25); assertThat(inbox(second)).hasSize(25);
        assertThat(inbox(first).getFirst().id()).isEqualTo(urgent);
        assertThat(inbox(first).getFirst().tags()).extracting(TagService.Tag::name).containsExactly("Prime");
        assertThat(inbox(first).stream().map(InboxPageService.Row::id).toList())
                .doesNotContainAnyElementsOf(inbox(second).stream().map(InboxPageService.Row::id).toList());
        assertThat(inbox(load("/inbox?size=50"))).hasSize(50);
        assertThat(inbox(load("/inbox?size=100"))).hasSize(56);
        assertThat(paging(load("/inbox?page=999")).number()).isEqualTo(3);
        assertThat(first.getResponse().getContentAsString()).doesNotContain("Not an incoming occurrence", "view-filters");
    }

    @Test void backlogPaginatesBothKindsTogetherWithoutChangingComposition() throws Exception {
        for (int i = 0; i < 52; i++) ordinary(owner, "Backlog " + i, i % 2 == 0 ? "BACKLOG" : "PAUSED");
        for (int i = 0; i < 3; i++) regular("Regular backlog " + i, "BACKLOG");
        regular("New series excluded", "NEW");
        ordinary(owner, "Finished excluded", "DONE");
        var first = load("/backlog"); var second = load("/backlog?page=2"); var third = load("/backlog?page=3");
        assertThat(paging(first).total()).isEqualTo(55); assertThat(backlog(first)).hasSize(25);
        assertThat(backlog(first).stream().filter(BacklogPageService.Row::recurring)).hasSize(3);
        assertThat(backlog(third)).hasSize(5);
        assertThat(Stream.of(first, second, third).flatMap(r -> backlog(r).stream()).map(r -> r.base()).distinct().count()).isEqualTo(55);
        assertThat(first.getModelAndView().getModel()).doesNotContainKeys("regularBacklog", "regularActive", "taskTags");
        assertThat(first.getResponse().getContentAsString()).doesNotContain("New series excluded", "Finished excluded");
    }

    @Test void filtersAndPageSizeArePreservedAndChangingProjectResetsPage() throws Exception {
        for (int i = 0; i < 27; i++) ordinary(owner, "Wanted " + i, "BACKLOG");
        db.update("UPDATE tasks SET planning_type='WANTED' WHERE project_id=?", project);
        String target = "/backlog?projectId=" + project + "&planningType=WANTED&size=50";
        var result = load(target + "&page=999"); var page = paging(result);
        assertThat(page.total()).isEqualTo(27); assertThat(page.number()).isEqualTo(1);
        assertThat(page.href(2)).contains("projectId=" + project, "planningType=WANTED", "size=50", "page=2");
        assertThat(page.sizeHref(100)).contains("projectId=" + project, "planningType=WANTED", "size=100").doesNotContain("page=");
        assertThat(page.projectHref(null)).contains("planningType=WANTED", "size=50").doesNotContain("page=", "projectId=");
        var nav = (AppNavigationService.Navigation) result.getModelAndView().getModel().get("appNav");
        assertThat(nav.projects()).allSatisfy(p -> assertThat(p.href()).contains("size=50").doesNotContain("page="));
    }

    @Test void editorAndActionReturnToCurrentPageAndLastPageShrinksSafely() throws Exception {
        for (int i = 0; i < 26; i++) ordinary(owner, "Incoming " + i, "NEW");
        String target = "/inbox?projectId=" + project + "&page=2";
        var row = inbox(load(target)).getFirst();
        String editor = mvc.perform(get(java.net.URI.create(row.href()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(editor).contains("name=\"redirectTo\"", "page=2");
        mvc.perform(post("/tasks/{id}", row.id()).with(csrf()).param("title", "Edited incoming").param("projectId", project.toString())
                .param("status", "NEW").param("redirectTo", target)).andExpect(redirectedUrl(target));
        mvc.perform(post("/tasks/{id}/closed", row.id()).with(csrf()).param("redirectTo", target)).andExpect(redirectedUrl(target));
        assertThat(paging(load(target)).number()).isEqualTo(1);
        assertThat(inbox(load(target))).hasSize(25);
        for (int i = 0; i < 26; i++) ordinary(owner, "Backlog " + i, "BACKLOG");
        String backlogTarget = "/backlog?projectId=" + project + "&page=2";
        var item = backlog(load(backlogTarget)).getFirst();
        mvc.perform(post("/tasks/{id}", item.id()).with(csrf()).param("title", "Edited backlog").param("projectId", project.toString())
                .param("status", "BACKLOG").param("redirectTo", backlogTarget)).andExpect(redirectedUrl(backlogTarget));
    }

    @Test void countsAndPagesCannotRevealHiddenTasksAndInputsAreValidated() throws Exception {
        db.update("INSERT INTO project_members(project_id,user_id,joined_at) VALUES(?,?,NOW())", project, other);
        db.update("UPDATE projects SET task_visibility='SCHEDULE_ONLY' WHERE id=?", project);
        for (String state : List.of("NEW", "BACKLOG")) {
            ordinary(owner, "Own " + state, state); ordinary(other, "Hidden " + state, state);
        }
        Long foreign = db.queryForObject("SELECT id FROM projects WHERE owner_user_id=? AND default_project", Long.class, other);
        for (String path : List.of("/inbox", "/backlog")) {
            assertThat(paging(load(path)).total()).isEqualTo(1);
            assertThat(load(path).getResponse().getContentAsString()).doesNotContain("Hidden NEW", "Hidden BACKLOG");
            mvc.perform(get(path).param("projectId", foreign.toString())).andExpect(status().isNotFound());
            for (String query : List.of("size=10", "size=0", "page=0", "page=-1", "page=invalid"))
                mvc.perform(get(path + "?" + query)).andExpect(status().isBadRequest());
        }
        db.execute("TRUNCATE users CASCADE"); owner = user("owner");
        for (String path : List.of("/inbox", "/backlog")) {
            var page = paging(load(path + "?page=999"));
            assertThat(page.total()).isZero(); assertThat(page.number()).isEqualTo(1); assertThat(page.from()).isZero();
        }
    }

    private MvcResult load(String path) throws Exception { return mvc.perform(get(path)).andExpect(status().isOk()).andReturn(); }
    private TaskListPaging paging(MvcResult r) { return (TaskListPaging) r.getModelAndView().getModel().get("pagination"); }
    @SuppressWarnings("unchecked") private List<InboxPageService.Row> inbox(MvcResult r) { return (List<InboxPageService.Row>) r.getModelAndView().getModel().get("inboxRows"); }
    @SuppressWarnings("unchecked") private List<BacklogPageService.Row> backlog(MvcResult r) { return (List<BacklogPageService.Row>) r.getModelAndView().getModel().get("backlogRows"); }
    private Long user(String login) { return db.queryForObject("INSERT INTO users(login,password_hash,created_at,updated_at) VALUES(?,'hash',NOW(),NOW()) RETURNING id", Long.class, login); }
    private Long ordinary(Long author, String title, String state) {
        Long number = nextNumber();
        return db.queryForObject("INSERT INTO tasks(user_id,project_id,title,status,task_number,task_key,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?) RETURNING id", Long.class,
                author, project, title, state, number, "QUEUE-" + number, LocalDateTime.of(2026, 4, 8, 10, 0), LocalDateTime.of(2026, 4, 8, 10, 0).plusMinutes(number));
    }
    private Long regular(String title, String state) {
        Long number = nextNumber();
        return db.queryForObject("INSERT INTO regular_tasks(user_id,project_id,title,status,task_number,task_key,recurrence_type,regular_status,created_at,updated_at,status_position) VALUES(?,?,?,?,?,?,'DAILY','INACTIVE',TIMESTAMP '2026-04-09 10:00',NOW(),1) RETURNING id",
                Long.class, owner, project, title, state, number, "QUEUE-REGULAR-" + number);
    }
    private Long nextNumber() { return db.queryForObject("UPDATE projects SET next_task_number=next_task_number+1 WHERE id=? RETURNING next_task_number-1", Long.class, project); }
}
