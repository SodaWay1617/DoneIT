package com.doneit.user.web;

import com.doneit.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RecurringCompletionSessionTest extends IntegrationTestSupport {
    @TestConfiguration
    static class FixedClock {
        @Bean @Primary Clock testClock() {
            return Clock.fixed(Instant.parse("2026-04-08T09:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    Long task;

    @BeforeEach void setup() throws Exception {
        db.execute("TRUNCATE users CASCADE");
        db.update("INSERT INTO users(login,password_hash,created_at,updated_at) VALUES('owner',?,NOW(),NOW())",
                passwords.encode("test-password"));
        db.update("UPDATE users SET display_name='Display Owner' WHERE login='owner'");
        Long project=db.queryForObject("SELECT id FROM projects WHERE default_project",Long.class);
        mvc.perform(post("/recurring").with(user("owner")).with(csrf()).param("title","Work")
                .param("projectId",project.toString()).param("status","IN_PROGRESS").param("recurrenceType","DAILY"))
                .andExpect(status().is3xxRedirection());
        task=db.queryForObject("SELECT id FROM regular_tasks WHERE title='Work'",Long.class);
        db.update("UPDATE regular_tasks SET activated_at=TIMESTAMP '2026-04-01 09:00:00' WHERE id=?",task);
    }

    @ParameterizedTest @CsvSource({"2026-04-07,en","2026-04-07,ru","2026-04-08,en","2026-04-08,ru"})
    void refreshRestoresRememberedSessionBeforeTrackingAndCompletingSelectedDay(String date,String language) throws Exception {
        var login=mvc.perform(post("/login").param("username","owner").param("password","test-password")
                        .param("remember-me","true").with(csrf())).andExpect(authenticated()).andReturn();
        Cookie remember=login.getResponse().getCookie("remember-me");
        var oldPage=mvc.perform(get("/tasks").param("date",date).param("lang",language)
                        .session((MockHttpSession)login.getRequest().getSession(false)))
                .andExpect(status().isOk()).andReturn();
        var oldToken=(org.springframework.security.web.csrf.CsrfToken)oldPage.getRequest().getAttribute("_csrf");
        assertThat(oldPage.getModelAndView().getModel().get("appUserLogin")).isEqualTo("owner");
        assertThat(oldPage.getResponse().getContentAsString()).contains("data-user=" + (char)34 + "owner" + (char)34);
        String localizedDate=LocalDate.parse(date).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        assertThat(oldPage.getResponse().getContentAsString()).doesNotContain("date="+localizedDate);
        var kanban=mvc.perform(get("/kanban").param("date",date).param("lang",language)
                .session((MockHttpSession)login.getRequest().getSession(false))).andExpect(status().isOk()).andReturn();
        assertThat(kanban.getResponse().getContentAsString()).contains("/kanban?date="+date)
                .doesNotContain("date="+localizedDate);
        String stale=oldToken.getToken();
        // A stale form without its old session is rejected before remember-me can restore login.
        mvc.perform(post("/recurring/{id}/done-today",task).cookie(remember).param("_csrf",stale)
                .param("date",date).param("spentMinutes","20")).andExpect(status().isForbidden());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM regular_task_completions",Integer.class)).isZero();

        var refreshed=mvc.perform(get("/api/csrf").cookie(remember))
                .andExpect(authenticated().withUsername("owner")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store")).andReturn();
        MockHttpSession session=(MockHttpSession)refreshed.getRequest().getSession(false);
        var token=json.readTree(refreshed.getResponse().getContentAsString());
        assertThat(token.get("parameterName").asText()).isEqualTo("_csrf");
        assertThat(token.get("username").asText()).isEqualTo("owner");
        String current=token.get("token").asText();
        mvc.perform(post("/recurring/{id}/track-time",task).session(session).param("_csrf","invalid")
                .param("date",date).param("minutes","25")).andExpect(status().isForbidden());
        mvc.perform(post("/recurring/{id}/track-time",task).session(session).param("_csrf",current)
                .param("date",date).param("minutes","25")).andExpect(status().is3xxRedirection());
        for(int repeat=0;repeat<2;repeat++)
            mvc.perform(post("/recurring/{id}/done-today",task).session(session).param("_csrf",current)
                    .param("date",date).param("spentMinutes","20").param("redirectTo","/tasks?date="+date))
                    .andExpect(redirectedUrl("/tasks?date="+date));
        assertThat(db.queryForObject("SELECT occurrence_date FROM regular_task_completions WHERE regular_task_id=?",
                LocalDate.class,task)).isEqualTo(LocalDate.parse(date));
        assertThat(db.queryForObject("SELECT SUM(minutes) FROM regular_task_time_entries WHERE regular_task_id=? AND entry_date=?",
                Integer.class,task,LocalDate.parse(date))).isEqualTo(45);
        assertThat(db.queryForObject("SELECT status FROM regular_tasks WHERE id=?",String.class,task)).isEqualTo("IN_PROGRESS");
        assertThat(db.queryForObject("SELECT regular_status FROM regular_tasks WHERE id=?",String.class,task)).isEqualTo("ACTIVE");
    }

    @Test void blankCompletionTimeAfterTrackingDoesNotLoseOrDuplicatePreviousDayTime() throws Exception {
        mvc.perform(post("/recurring/{id}/track-time",task).with(user("owner")).with(csrf())
                .param("date","2026-04-07").param("minutes","60")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/recurring/{id}/done-today",task).with(user("owner")).with(csrf())
                .param("date","2026-04-07").param("spentMinutes","")).andExpect(status().is3xxRedirection());
        assertThat(db.queryForObject("SELECT SUM(minutes) FROM regular_task_time_entries WHERE regular_task_id=?",
                Integer.class,task)).isEqualTo(60);
        assertThat(db.queryForObject("SELECT occurrence_date FROM regular_task_completions WHERE regular_task_id=?",
                LocalDate.class,task)).isEqualTo(LocalDate.of(2026,4,7));
    }

    @Test void csrfRefreshRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/csrf")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/csrf").with(user("owner"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.parameterName").value("_csrf"))
                .andExpect(jsonPath("$.username").value("owner"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }
}
