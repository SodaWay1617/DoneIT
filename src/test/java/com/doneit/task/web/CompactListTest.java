package com.doneit.task.web;

import com.doneit.project.application.ProjectService;
import com.doneit.support.IntegrationTestSupport;
import com.doneit.tag.application.TagService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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
class CompactListTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ProjectService projects;
    @Autowired TagService tags;
    Long project, otherProject, ordinary, regular, prime, health;

    @BeforeEach void setUp() throws Exception {
        db.execute("TRUNCATE TABLE users CASCADE");
        db.update("INSERT INTO users(login,password_hash,display_name,created_at,updated_at) VALUES('owner','hash','Owner',NOW(),NOW())");
        projects.create("Health","HEALTH",null); projects.create("Other","OTHER",null);
        project=db.queryForObject("SELECT id FROM projects WHERE code='HEALTH'",Long.class);
        otherProject=db.queryForObject("SELECT id FROM projects WHERE code='OTHER'",Long.class);
        ordinary=create("Ordinary","REQUIRED",project);
        create("Wanted","WANTED",project);create("Optional","OPTIONAL",otherProject);
        mvc.perform(post("/recurring").with(csrf()).param("projectId",project.toString()).param("title","Regular")
                .param("status","IN_PROGRESS").param("recurrenceType","DAILY").param("occurrenceTime","09:00")
                .param("estimateMinutes","20").param("tagNames","Prime")).andExpect(status().is3xxRedirection());
        regular=db.queryForObject("SELECT id FROM regular_tasks WHERE title='Regular'",Long.class);
        prime=db.queryForObject("SELECT id FROM tags WHERE name='Prime'",Long.class);
        asOwner();
        health=tags.create("Health").id();
        tags.replace(ordinary,false,List.of("Prime","Health"));
    }

    @Test void categoriesTagsAndProjectCombineWithoutChangingOriginalSort() throws Exception {
        var filtered=model("/?projectId="+project+"&planningType=REQUIRED&tagId="+prime+"&tagId="+health);
        assertThat(rows(filtered)).extracting(CompactTaskRow::title).containsExactly("Regular","Ordinary");
        Map<String,Long> counts=(Map<String,Long>)filtered.get("compactCounts");
        assertThat(counts).containsEntry("ALL",2L).containsEntry("REQUIRED",2L).containsEntry("WANTED",0L);
        assertThat(filtered.get("compactReturnTo")).isEqualTo("/?projectId="+project+"&planningType=REQUIRED&tagId="+prime+"&tagId="+health);
        assertThat(filtered.get("compactResetTo")).isEqualTo("/?projectId="+project);
        assertThat(rows(model("/?planningType=WANTED&tagId="+prime))).isEmpty();
        assertThat(rows(model("/?tagId="+health))).extracting(CompactTaskRow::title).containsExactly("Ordinary");
    }

    @Test void rendersCompactMetadataAndCorrectRecurringActions() throws Exception {
        String html=mvc.perform(get("/").param("lang","ru")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("/recurring/"+regular+"/done-today","/recurring/"+regular+"/skip-today",
                "role=\"checkbox\" aria-checked=\"false\"")
                .doesNotContain("/recurring/"+regular+"/closed","/recurring/"+regular+"/reschedule",
                    "id=\"inbox-preview\"","id=\"backlog-preview\"","id=\"completed-tasks\"");
        var row=rows(model("/")).stream().filter(CompactTaskRow::recurring).findFirst().orElseThrow();
        assertThat(row.interval()).isEqualTo("09:00–09:20");
        assertThat(row.projectName()).isEqualTo("Health");
    }

    @Test void projectDropdownUsesExistingCombinedFilterAndAllowsAllProjects() throws Exception {
        String html=mvc.perform(get("/tasks").param("date","2026-04-08")
                .param("projectId",project.toString()).param("planningType","REQUIRED").param("tagId",prime.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String filters=html.substring(html.indexOf("id=\"compact-filters\""));
        filters=filters.substring(0,filters.indexOf("</form>"));
        assertThat(filters).contains("<select name=\"projectId\"", "value=\""+project+"\" selected=\"selected\"",
                "value=\""+otherProject+"\"", "value=\"2026-04-08\"", "name=\"tagId\"")
                .doesNotContain("type=\"hidden\" name=\"projectId\"");
        assertThat(rows(model("/tasks?date=2026-04-08&projectId=&planningType=OPTIONAL")))
                .extracting(CompactTaskRow::title).containsExactly("Optional");
    }

    @Test void closedAndBacklogStayOutOfMainListAndPastSkipIsUnavailable() throws Exception {
        db.update("UPDATE tasks SET status='BACKLOG',planned_date=NULL WHERE id=?",ordinary);
        assertThat(rows(model("/"))).noneMatch(row->!row.recurring()&&row.id().equals(ordinary));
        db.update("UPDATE regular_tasks SET activated_at=TIMESTAMP '2026-04-01 09:00:00' WHERE id=?",regular);
        String html=mvc.perform(get("/tasks?date=2026-04-07")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).containsPattern("(?s)action=\"/recurring/"+regular+"/skip-today\".*?<button[^>]*disabled");
    }

    @Test void selectedTagsCannotRevealSomeoneElsesLabels() throws Exception {
        db.update("INSERT INTO users(login,password_hash,created_at,updated_at) VALUES('other','hash',NOW(),NOW())");
        Long foreign=db.queryForObject("INSERT INTO tags(user_id,name,name_key) SELECT id,'Secret','secret' FROM users WHERE login='other' RETURNING id",Long.class);
        mvc.perform(get("/?tagId="+foreign)).andExpect(status().isNotFound());
        mvc.perform(get("/?tagId=NaN")).andExpect(status().isBadRequest());
    }

    @Test void reschedulingPreservesDeadlinePriorityLabelsAndExactTime() throws Exception {
        db.update("UPDATE tasks SET planned_date=NULL,planned_for_at=TIMESTAMP '2026-04-08 09:15:00',deadline_at=TIMESTAMP '2026-04-20 12:00:00',priority='HIGH' WHERE id=?",ordinary);
        String back="/?tagId="+prime;
        mvc.perform(post("/tasks/{id}/reschedule",ordinary).with(csrf()).param("plannedDate","2026-04-09")
                .param("plannedWithTime","true").param("plannedForAt","2026-04-09T09:15").param("redirectTo",back))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(back));
        var record=db.queryForMap("SELECT planned_for_at,deadline_at,priority,project_id FROM tasks WHERE id=?",ordinary);
        assertThat(record.get("priority")).isEqualTo("HIGH");
        assertThat(record.get("project_id")).isEqualTo(project);
        assertThat(record.get("deadline_at").toString()).startsWith("2026-04-20");
        assertThat(record.get("planned_for_at").toString()).startsWith("2026-04-09 09:15");
        asOwner();
        assertThat(tags.names(ordinary,false)).containsExactly("Health","Prime");
    }

    private Long create(String title,String planning,Long project) throws Exception {
        asOwner();
        mvc.perform(post("/tasks").with(csrf()).param("title",title).param("projectId",project.toString())
                .param("status","IN_PROGRESS").param("planningType",planning).param("plannedDate","2026-04-08"))
                .andExpect(status().is3xxRedirection());
        return db.queryForObject("SELECT id FROM tasks WHERE title=?",Long.class,title);
    }
    private Map<String,Object> model(String path) throws Exception {
        try { return mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getModelAndView().getModel(); }
        finally { asOwner(); }
    }
    private void asOwner() {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.TestingAuthenticationToken("owner","password","ROLE_USER"));
    }
    @SuppressWarnings("unchecked") private List<CompactTaskRow> rows(Map<String,Object> model) {
        return (List<CompactTaskRow>)model.get("compactRows");
    }
}
