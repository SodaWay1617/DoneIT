package com.doneit.task.web;

import com.doneit.project.domain.Project;
import com.doneit.support.IntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username="owner")
class ProjectCodeTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;

    @BeforeEach void setup() {
        db.execute("TRUNCATE users CASCADE");
        db.update("INSERT INTO users(login,password_hash,created_at,updated_at) VALUES('owner','hash',NOW(),NOW()),('other','hash',NOW(),NOW()),('member','hash',NOW(),NOW())");
    }

    @Test void differentOwnersCanCreateAndEditTheSameVisibleCodeWithoutSharingAccess() throws Exception {
        Long own=create("owner","WORK");
        Long foreign=create("other","OTHER");
        mvc.perform(post("/projects/{id}",foreign).with(user("other")).with(csrf()).param("name","Other work").param("code","work"))
                .andExpect(redirectedUrl("/projects/"+foreign));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM projects WHERE code='WORK'",Integer.class)).isEqualTo(2);
        var visible=mvc.perform(get("/projects")).andExpect(status().isOk()).andReturn().getModelAndView().getModel().get("projects");
        assertThat((List<?>)visible).extracting(p->((Project)p).id()).contains(own).doesNotContain(foreign);
        mvc.perform(post("/projects/{id}",foreign).with(csrf()).param("name","Forbidden").param("code","WORK").param("taskVisibility","ALL_MEMBERS"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/inbox").param("projectId",foreign.toString())).andExpect(status().isNotFound());
        assertThat(db.queryForObject("SELECT name FROM projects WHERE id=?",String.class,foreign)).isEqualTo("Other work");
    }

    @Test void duplicateCreateIsLocalizedAndKeepsSubmittedFields() throws Exception {
        create("owner","WORK");
        var result=mvc.perform(post("/projects").with(csrf()).param("name","Another work").param("code"," work ")
                .param("description","Keep this description").param("taskVisibility","SCHEDULE_ONLY"))
                .andExpect(redirectedUrl("/projects#new-project")).andExpect(flash().attribute("projectCodeConflict",true)).andReturn();
        mvc.perform(get("/projects?lang=en").session((MockHttpSession)result.getRequest().getSession(false)))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("You already own a project with this code")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"Another work\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Keep this description")));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM projects WHERE code='WORK'",Integer.class)).isEqualTo(1);
    }

    @Test void duplicateUpdateRollsBackAndRetainsTheDraft() throws Exception {
        create("owner","WORK"); Long second=create("owner","HOME");
        var result=mvc.perform(post("/projects/{id}",second).with(csrf()).param("name","Draft name").param("code","WORK"))
                .andExpect(redirectedUrl("/projects/"+second)).andExpect(flash().attribute("projectCodeConflict",true)).andReturn();
        mvc.perform(get("/projects/"+second+"?lang=ru").session((MockHttpSession)result.getRequest().getSession(false)))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("У вас уже есть проект с таким кодом")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value='Draft name'")));
        assertThat(db.queryForObject("SELECT code FROM projects WHERE id=?",String.class,second)).isEqualTo("HOME");
    }

    @Test void sharedMemberCanCreateOrdinaryAndRecurringTasksInBothSameCodeProjects() throws Exception {
        Long first=create("owner","WORK"),second=create("other","WORK");
        Long member=db.queryForObject("SELECT id FROM users WHERE login='member'",Long.class);
        for(Long project:List.of(first,second)) {
            db.update("INSERT INTO project_members VALUES(?,?,NOW())",project,member);
            mvc.perform(post("/tasks").with(user("member")).with(csrf()).param("projectId",project.toString())
                    .param("title","Ordinary "+project).param("status","NEW")).andExpect(status().is3xxRedirection());
            mvc.perform(post("/recurring").with(user("member")).with(csrf()).param("projectId",project.toString())
                    .param("title","Regular "+project).param("status","BACKLOG").param("recurrenceType","DAILY"))
                    .andExpect(status().is3xxRedirection());
        }
        var ordinary=db.queryForList("SELECT task_key FROM tasks ORDER BY id",String.class);
        var regular=db.queryForList("SELECT task_key FROM regular_tasks ORDER BY id",String.class);
        assertThat(ordinary).hasSize(2).doesNotHaveDuplicates().allSatisfy(k->assertThat(k).startsWith("WORK-1___member___project-"));
        assertThat(regular).hasSize(2).doesNotHaveDuplicates().allSatisfy(k->assertThat(k).startsWith("WORK-2___member___project-"));
        mvc.perform(get("/inbox?projectId="+second).with(user("member"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ordinary "+second)))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Ordinary "+first))));
    }

    @Test void renamingProjectPreservesLegacyTaskKeyIdAndHistoryAndEditorLink() throws Exception {
        Long project=create("owner","WORK");
        mvc.perform(post("/tasks").with(csrf()).param("projectId",project.toString()).param("title","Legacy task").param("status","NEW"))
                .andExpect(status().is3xxRedirection());
        Long task=db.queryForObject("SELECT id FROM tasks",Long.class);
        db.update("UPDATE tasks SET task_key='WORK-1___owner',status='BACKLOG' WHERE id=?",task);
        var history=db.queryForList("SELECT * FROM task_change_history ORDER BY id");
        mvc.perform(post("/projects/{id}",project).with(csrf()).param("name","Renamed work").param("code","RENAMED"))
                .andExpect(redirectedUrl("/projects/"+project));
        assertThat(db.queryForObject("SELECT task_key FROM tasks WHERE id=?",String.class,task)).isEqualTo("WORK-1___owner");
        assertThat(db.queryForList("SELECT * FROM task_change_history ORDER BY id")).isEqualTo(history);
        mvc.perform(get("/tasks/{id}/edit",task)).andExpect(status().isOk());
        mvc.perform(get("/tasks/{id}/edit",task).with(user("other"))).andExpect(status().isNotFound());
    }

    private Long create(String login,String code) throws Exception {
        mvc.perform(post("/projects").with(user(login)).with(csrf()).param("name",login+" "+code).param("code",code))
                .andExpect(redirectedUrl("/projects"));
        return db.queryForObject("SELECT p.id FROM projects p JOIN users u ON u.id=p.owner_user_id WHERE u.login=? AND p.code=?",Long.class,login,code);
    }
}
