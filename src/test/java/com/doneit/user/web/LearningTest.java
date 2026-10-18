package com.doneit.user.web;

import com.doneit.support.IntegrationTestSupport;
import com.doneit.user.application.LearningService;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LearningTest extends IntegrationTestSupport {
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired LearningService learning;
    @Autowired MessageSource messages;

    @BeforeEach void setup() {
        db.execute("TRUNCATE users CASCADE");
        for (String login : new String[]{"owner", "katya"})
            db.update("INSERT INTO users(login,password_hash,created_at,updated_at) VALUES(?,'unused',NOW(),NOW())", login);
    }

    @Test void newUsersGetInvitationButCanAlwaysOpenLearningFromSettings() throws Exception {
        assertThat(learning.progress("owner").invite()).isTrue();
        mvc.perform(get("/").with(user("owner"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"learning-invite\"")));
        mvc.perform(get("/settings").with(user("owner"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/learn\"")));
    }

    @Test void readingIsExplicitIdempotentPersistentAndPersonal() throws Exception {
        mvc.perform(get("/learn/0").with(user("owner"))).andExpect(status().isOk());
        assertThat(learning.progress("owner").count()).isZero();
        for (int repeat = 0; repeat < 2; repeat++)
            mvc.perform(post("/learn/0/read").with(user("owner")).with(csrf()))
                    .andExpect(redirectedUrl("/learn"));
        assertThat(new LearningService(db).progress("owner").count()).isEqualTo(1);
        assertThat(learning.progress("katya").count()).isZero();
        mvc.perform(get("/learn").with(user("owner"))).andExpect(redirectedUrl("/learn/1"));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM tasks", Integer.class)).isZero();
    }

    @Test void dismissalAndRestartDoNotChangeAnotherUsersState() throws Exception {
        learning.markRead("owner", 2);
        mvc.perform(post("/learn/dismiss").with(user("owner")).with(csrf())).andExpect(redirectedUrl("/"));
        assertThat(learning.progress("owner").invite()).isFalse();
        assertThat(learning.progress("owner").read(2)).isTrue();
        assertThat(learning.progress("katya").invite()).isTrue();
        mvc.perform(get("/").with(user("owner"))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"learning-invite\""))));
        mvc.perform(get("/learn/2").with(user("owner"))).andExpect(status().isOk());
        mvc.perform(post("/learn/restart").with(user("owner")).with(csrf())).andExpect(redirectedUrl("/learn/0"));
        assertThat(learning.progress("owner").count()).isZero();
        assertThat(learning.progress("owner").invite()).isTrue();
    }

    @Test void resumedInvitationAndBothDismissButtonsPreserveReadProgress() throws Exception {
        learning.markRead("owner", 0);
        String html = mvc.perform(get("/").param("lang", "en").with(user("owner")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String invitation = html.substring(html.indexOf("<aside class=\"today-onboarding\""), html.indexOf("<div class=\"grid\""));
        assertThat(invitation).contains("Continue exploring DoneIt", "Continue learning",
                "today-learning-dismiss", "Hide learning invitation");
        assertThat(invitation.split("action=\"/learn/dismiss\"", -1)).hasSize(3);
        mvc.perform(get("/learn").with(user("owner"))).andExpect(redirectedUrl("/learn/1"));
        mvc.perform(post("/learn/dismiss").with(user("owner")).with(csrf())).andExpect(redirectedUrl("/"));
        assertThat(learning.progress("owner").read(0)).isTrue();
        assertThat(learning.progress("owner").complete()).isFalse();
        mvc.perform(get("/").with(user("owner"))).andExpect(content().string(not(containsString("today-onboarding"))));
    }

    @Test void allTopicsRenderInBothLanguagesAndCompletionHidesInvitation() throws Exception {
        for (String language : new String[]{"ru", "en"}) {
            for (int topic = 0; topic < LearningService.TOPIC_COUNT; topic++) {
                String title = messages.getMessage("learn." + topic + ".title", null, Locale.forLanguageTag(language));
                mvc.perform(get("/learn/" + topic).param("lang", language).with(user("owner")))
                        .andExpect(status().isOk()).andExpect(content().string(containsString(title)))
                        .andExpect(content().string(containsString("name=\"_csrf\"")));
            }
        }
        for (int topic = 0; topic < LearningService.TOPIC_COUNT; topic++) learning.markRead("owner", topic);
        assertThat(learning.progress("owner").complete()).isTrue();
        assertThat(learning.progress("owner").invite()).isFalse();
        mvc.perform(get("/learn/7").with(user("owner"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("role=\"status\"")));
    }

    @Test void authenticationCsrfAndTopicBoundsAreEnforced() throws Exception {
        mvc.perform(get("/learn")).andExpect(status().is3xxRedirection());
        for (String path : new String[]{"/learn/0/read", "/learn/dismiss", "/learn/restart"})
            mvc.perform(post(path).with(user("owner"))).andExpect(status().isForbidden());
        for (int topic : new int[]{-1, 8, 100}) {
            mvc.perform(get("/learn/" + topic).with(user("owner"))).andExpect(status().isNotFound());
            mvc.perform(post("/learn/" + topic + "/read").with(user("owner")).with(csrf()))
                    .andExpect(status().isNotFound());
        }
        assertThat(learning.progress("owner").count()).isZero();
    }
}
