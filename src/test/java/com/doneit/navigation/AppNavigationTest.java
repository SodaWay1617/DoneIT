package com.doneit.navigation;

import com.doneit.navigation.AppNavigationService.Navigation;
import com.doneit.support.IntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AppNavigationTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    Long main;
    Long shared;
    int sequence;

    @BeforeEach void setup() throws Exception {
        db.execute("TRUNCATE users CASCADE");
        for (String login : List.of("owner", "katya", "outsider"))
            db.update("INSERT INTO users(login,password_hash,created_at,updated_at) VALUES(?,'unused',NOW(),NOW())", login);
        main = db.queryForObject("SELECT id FROM projects WHERE owner_user_id=(SELECT id FROM users WHERE login='owner')", Long.class);
        mvc.perform(post("/projects").with(user("owner")).with(csrf()).param("name", "Family").param("code", "FAMILY"))
                .andExpect(status().is3xxRedirection());
        shared = db.queryForObject("SELECT id FROM projects WHERE name='Family'", Long.class);
        db.update("INSERT INTO project_members VALUES(?,(SELECT id FROM users WHERE login='katya'),NOW())", shared);
    }

    @Test void commonShellRendersOnEveryWorkingPageInBothLanguages() throws Exception {
        Long task = create("owner", "TODO", main, false);
        Long regular = create("owner", "NEW", main, true);
        List<String> pages = List.of("/", "/tasks?date=2026-10-18", "/inbox", "/backlog", "/finished", "/kanban", "/calendar", "/schedule",
                "/analytics", "/analytics/workload", "/reports", "/projects", "/projects/" + shared, "/settings", "/settings/devices", "/learn/0",
                "/tasks/new", "/recurring/new", "/tasks/" + task + "/edit", "/recurring/" + regular + "/edit");
        for (String language : List.of("ru", "en")) {
            for (String path : pages)
                mvc.perform(get(path).with(user("owner")).param("lang", language)).andExpect(status().isOk())
                        .andExpect(content().string(containsString("class=\"app-navigation\"")))
                        .andExpect(content().string(containsString("id=\"app-main\"")))
                        .andExpect(content().string(containsString("aria-controls=\"app-more\"")));
        }
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(not(containsString("class=\"app-navigation\""))));
    }

    @Test void projectsIncludeSharedButNeverAnotherUsersPrivateMain() throws Exception {
        var nav = nav("katya", "/settings");
        assertThat(nav.projects()).extracting(AppNavigationService.ProjectItem::name).containsExactly("MAIN", "Family");
        assertThat(nav.projects()).extracting(AppNavigationService.ProjectItem::id).doesNotContain(main);
        assertThat(nav("outsider", "/").projects()).hasSize(1);
    }

    @Test void countersCombineOrdinaryAndRegularTasksAndRespectVisibility() throws Exception {
        create("owner", "NEW", main, false);
        create("owner", "NEW", main, true);
        create("owner", "BACKLOG", main, false);
        create("owner", "PAUSED", main, false);
        create("owner", "BACKLOG", main, true);
        Long delegated = create("owner", "NEW", shared, false);
        create("katya", "NEW", shared, false);
        assertThat(nav("owner", "/").main().get(1).count()).isEqualTo(4);
        assertThat(nav("owner", "/").main().get(2).count()).isEqualTo(3);
        assertThat(nav("katya", "/").main().get(1).count()).isEqualTo(2);
        db.update("UPDATE projects SET task_visibility='AUTHOR_AND_ASSIGNEES' WHERE id=?", shared);
        assertThat(nav("owner", "/").main().get(1).count()).isEqualTo(3);
        assertThat(nav("katya", "/").main().get(1).count()).isEqualTo(1);
        db.update("INSERT INTO task_assignees(task_id,user_id,assigned_at) VALUES(?,(SELECT id FROM users WHERE login='katya'),NOW())", delegated);
        assertThat(nav("katya", "/").main().get(1).count()).isEqualTo(2);
        assertThat(nav("outsider", "/").main().get(1).count()).isZero();
    }

    @Test void selectedProjectAndPlanningTypeArePreservedInLinksAndCounters() throws Exception {
        create("owner", "NEW", main, false);
        Long optional = create("owner", "NEW", shared, false);
        db.update("UPDATE tasks SET planning_type='OPTIONAL' WHERE id=?", optional);
        var nav = nav("owner", "/kanban?projectId=" + shared + "&planningType=OPTIONAL&date=2026-11-04");
        assertThat(nav.main().get(1).count()).isEqualTo(1);
        assertThat(nav.main().get(3).href()).contains("projectId=" + shared, "planningType=OPTIONAL", "month=2026-11");
        assertThat(nav.listHref()).contains("date=2026-11-04");
        assertThat(nav.projects().stream().filter(AppNavigationService.ProjectItem::active).map(AppNavigationService.ProjectItem::id)).containsExactly(shared);
        assertThat(nav.allProjectsHref()).doesNotContain("projectId").contains("planningType=OPTIONAL");
        assertThat(nav.russianHref()).contains("/kanban?", "projectId=" + shared, "date=2026-11-04", "planningType=OPTIONAL", "lang=ru");
        assertThat(nav("owner", "/inbox?projectId=" + shared + "&planningType=REQUIRED").main().get(1).count()).isZero();
    }

    @Test void activeSectionsAreServerRenderedIncludingMoreAndAnalyticsChildren() throws Exception {
        for (String section : List.of("inbox", "backlog", "calendar", "schedule", "kanban", "finished", "projects", "settings")) {
            var nav = nav("owner", "/" + section);
            assertThat(nav.section()).isEqualTo(section);
            assertThat(nav.moreActive()).isEqualTo(!List.of("inbox", "calendar", "projects").contains(section));
        }
        var workload = nav("owner", "/analytics/workload");
        assertThat(workload.section()).isEqualTo("analytics");
        assertThat(workload.analytics().get(1).active()).isTrue();
        assertThat(nav("owner", "/settings/devices").section()).isEqualTo("settings");
        assertThat(nav("owner", "/learn/2").section()).isEqualTo("settings");
    }

    @Test void zeroBadgesAreHiddenAndCurrentCountsUpdateAfterStatusChanges() throws Exception {
        mvc.perform(get("/").with(user("owner"))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("class=\"app-badge\""))));
        Long task = create("owner", "NEW", main, false);
        assertThat(nav("owner", "/").main().get(1).count()).isEqualTo(1);
        db.update("UPDATE tasks SET status='TODO' WHERE id=?", task);
        assertThat(nav("owner", "/").main().get(1).count()).isZero();
    }

    @Test void accountActionsAndLearningLiveOnlyInSettings() throws Exception {
        var result = mvc.perform(get("/").with(user("owner"))).andExpect(status().isOk()).andReturn();
        String html = result.getResponse().getContentAsString();
        String navigation = html.substring(html.indexOf("class=\"app-navigation\""), html.indexOf("id=\"app-main\""));
        assertThat(navigation).contains("href=\"/settings\"").doesNotContain("href=\"/learn\"", "action=\"/logout\"", "lang=\"ru\"", "app-topbar-user");
        assertThat(nav("owner", "/").more()).extracting(AppNavigationService.Item::key).doesNotContain("learn");
        mvc.perform(get("/settings").with(user("owner"))).andExpect(status().isOk())
                .andExpect(model().attribute("profileLogin", "owner"))
                .andExpect(model().attribute("profileName", "owner"))
                .andExpect(content().string(containsString("href=\"/learn\"")))
                .andExpect(content().string(containsString("action=\"/logout\"")))
                .andExpect(content().string(containsString("href=\"/settings/devices\"")))
                .andExpect(content().string(containsString("href=\"/settings?lang=ru\"")));
    }

    @Test void projectListStartsCollapsedAndSupportsKeyboardScrolling() throws Exception {
        String html=mvc.perform(get("/").with(user("owner"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("<details class=\"app-project-disclosure\">",
                "class=\"app-project-scroll\" tabindex=\"0\" role=\"region\"")
                .doesNotContain("class=\"app-project-disclosure\" open");
    }

    private Navigation nav(String login, String path) throws Exception {
        var result = mvc.perform(get(path).with(user(login))).andExpect(status().isOk()).andReturn();
        return (Navigation) result.getModelAndView().getModel().get("appNav");
    }

    private Long create(String login, String status, Long project, boolean regular) throws Exception {
        mvc.perform(post(regular ? "/recurring" : "/tasks").with(user(login)).with(csrf())
                .param("title", "Task " + ++sequence).param("projectId", project.toString()).param("status", status)
                .param("withoutPlannedDate", "true").param("withoutDeadline", "true"))
                .andExpect(status().is3xxRedirection());
        return db.queryForObject("SELECT MAX(id) FROM " + (regular ? "regular_tasks" : "tasks"), Long.class);
    }
}
