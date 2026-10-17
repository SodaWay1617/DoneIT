package com.doneit.task.web;

import com.doneit.notifications.application.SiteReminderService;
import com.doneit.project.application.ProjectService;
import com.doneit.project.domain.TaskVisibilityMode;
import com.doneit.support.IntegrationTestSupport;
import java.time.*;
import java.util.Locale;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CalendarHistoryTest.FixedClockConfiguration.class)
@WithMockUser(username="owner")
class SiteRemindersTest extends IntegrationTestSupport {
    @Autowired JdbcTemplate db;
    @Autowired ProjectService projects;
    @Autowired SiteReminderService reminders;
    @Autowired MockMvc mvc;
    Long owner,katya,project;
    LocalDate today=LocalDate.of(2026,4,8);
    @BeforeEach void setup() {
        db.execute("TRUNCATE users CASCADE");
        owner=user("owner");katya=user("katya");
        projects.create("Home","HOME",null);
        project=db.queryForObject("SELECT id FROM projects WHERE code='HOME'",Long.class);
        projects.add(project,katya);
    }
    @Test void showsDeadlineGroupsAndScheduledTimeWithoutDuplicatingTasks() {
        Long overdue=task(owner,"Overdue","TODO",today.atTime(10,0));deadline(overdue,today.minusDays(1));
        Long current=task(owner,"Today","TODO",null);deadline(current,today);
        Long tomorrow=task(owner,"Tomorrow","TODO",null);deadline(tomorrow,today.plusDays(1));
        task(owner,"Timed","IN_PROGRESS",today.atTime(9,30));
        var result=reminders.current(Locale.ENGLISH);
        assertThat(result.items()).extracting(SiteReminderService.Reminder::title).containsExactly("Overdue","Today","Tomorrow","Timed");
        assertThat(result.items().getLast().message()).isEqualTo("Today at 09:30");
        assertThat(result.day()).isEqualTo(today);
        assertThat(result.userId()).isEqualTo(owner);
    }
    @Test void importantUpcomingTasksAreLimitedToSevenDaysAndActiveStatuses() {
        Long upcoming=task(owner,"Important","TODO",today.plusDays(7).atTime(9,0));
        db.update("UPDATE tasks SET priority='HIGH' WHERE id=?",upcoming);
        Long later=task(owner,"Later","TODO",today.plusDays(8).atTime(9,0));
        db.update("UPDATE tasks SET priority='CRITICAL' WHERE id=?",later);
        task(owner,"Normal","TODO",today.plusDays(1).atTime(9,0));
        task(owner,"Finished","DONE",today.atTime(9,0));
        task(owner,"Cancelled","CLOSED",today.atTime(9,0));
        task(owner,"Undated","IN_PROGRESS",null);
        assertThat(reminders.current(Locale.ENGLISH).items()).extracting(SiteReminderService.Reminder::title).containsExactly("Important");
    }
    @Test void sharedVisibilityDoesNotNotifyPeopleWhoAreNotResponsible() {
        task(katya,"Foreign","TODO",today.atTime(9,0));
        Long delegated=task(owner,"Delegated","TODO",today.atTime(9,0));
        db.update("DELETE FROM task_assignees WHERE task_id=?",delegated);assign(delegated,false,katya);
        Long both=task(owner,"Both","TODO",today.atTime(9,0));assign(both,false,katya);
        assertThat(reminders.current(Locale.ENGLISH).items()).extracting(SiteReminderService.Reminder::title).containsExactly("Both");
        projects.update(project,"Home","HOME",null,TaskVisibilityMode.AUTHOR_AND_ASSIGNEES);
        assertThat(reminders.current(Locale.ENGLISH).items()).hasSize(1);
    }
    @Test void regularDoneTodaySkippedAndForeignInstancesAreExcluded() {
        Long active=regular("Active",owner),done=regular("Done today",owner),skip=regular("Skipped",owner);
        regular("Foreign",katya);
        db.update("INSERT INTO regular_task_completions VALUES(?,?,NOW())",done,today);
        db.update("INSERT INTO regular_task_skips VALUES(?,?,NOW())",skip,today);
        var result=reminders.current(Locale.ENGLISH);
        assertThat(result.items()).extracting(SiteReminderService.Reminder::title).containsExactly("Active");
        assertThat(result.items().getFirst().href()).isEqualTo("/recurring/"+active+"/edit");
    }
    @Test void responseIsLocalizedBoundedAndNeverCached() throws Exception {
        for(int i=0;i<7;i++) task(owner,"Task "+i,"TODO",today.atTime(9,i));
        var result=reminders.current(Locale.forLanguageTag("ru"));
        assertThat(result.items()).hasSize(5);
        assertThat(result.more()).isEqualTo("Других напоминаний: 2");
        mvc.perform(get("/api/reminders").param("lang","ru"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.heading").value("Напоминания"))
                .andExpect(jsonPath("$.items.length()").value(5));
        mvc.perform(get("/api/reminders").param("lang","en"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.heading").value("Reminders"));
    }
    @Test void emptyAccountHasNoReminders() {assertThat(reminders.current(Locale.ENGLISH).items()).isEmpty();}
    @Test void anonymousRequestsCannotReadRemindersAndUserIdCannotOverrideTheViewer() throws Exception {
        task(katya,"Private foreign task","TODO",today.atTime(9,0));
        mvc.perform(get("/api/reminders").param("userId",katya.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/reminders").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(status().is3xxRedirection());
    }
    private Long user(String login) {return db.queryForObject("INSERT INTO users(login,password_hash,created_at,updated_at) VALUES(?,'hash',NOW(),NOW()) RETURNING id",Long.class,login);}
    private long number() {return db.queryForObject("UPDATE projects SET next_task_number=next_task_number+1 WHERE id=? RETURNING next_task_number-1",Long.class,project);}
    private Long task(Long author,String title,String status,LocalDateTime planned) {
        return db.queryForObject("INSERT INTO tasks(user_id,project_id,title,status,planned_for_at,task_number,task_key,created_at,updated_at) VALUES(?,?,?,?,?,?,?,NOW(),NOW()) RETURNING id",
                Long.class,author,project,title,status,planned,number(),title+"___"+author);
    }
    private void deadline(Long id,LocalDate day) {db.update("UPDATE tasks SET deadline_at=? WHERE id=?",day.atTime(23,59),id);}
    private void assign(Long id,boolean regular,Long person) {db.update("INSERT INTO task_assignees("+(regular?"regular_task_id":"task_id")+",user_id,assigned_at) VALUES(?,?,NOW())",id,person);}
    private Long regular(String title,Long author) {
        return db.queryForObject("""
                INSERT INTO regular_tasks(user_id,project_id,title,status,task_number,task_key,recurrence_type,regular_status,
                    occurrence_time,activated_at,created_at,updated_at,status_position)
                VALUES(?,?,?,'IN_PROGRESS',?,?,'DAILY','ACTIVE',TIME '09:00',?,NOW(),NOW(),1) RETURNING id
                """,Long.class,author,project,title,number(),title+"___"+author,today.minusDays(1).atStartOfDay());
    }
}
