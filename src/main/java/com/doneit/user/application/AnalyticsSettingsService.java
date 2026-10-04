package com.doneit.user.application;

import com.doneit.project.application.ProjectService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AnalyticsSettingsService {
    private final JdbcTemplate db;
    private final ProjectService projects;
    public AnalyticsSettingsService(JdbcTemplate db, ProjectService projects) { this.db = db; this.projects = projects; }
    public boolean rollUpSubtaskTime() {
        return Boolean.TRUE.equals(db.queryForObject("SELECT roll_up_subtask_time FROM users WHERE id=?",
                Boolean.class, projects.user().id()));
    }
    @Transactional
    public void update(boolean enabled) {
        db.update("UPDATE users SET roll_up_subtask_time=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                enabled, projects.user().id());
    }
}
