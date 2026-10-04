package com.doneit.user.application;

import com.doneit.project.application.ProjectService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkloadSettingsService {
    private final JdbcTemplate db;
    private final ProjectService projects;

    public WorkloadSettingsService(JdbcTemplate db, ProjectService projects) {
        this.db = db;
        this.projects = projects;
    }

    public WorkloadSettings current() {
        return db.queryForObject("""
                SELECT workload_green_minutes,workload_yellow_minutes,workload_orange_minutes
                FROM users WHERE id=?
                """, (rs, row) -> new WorkloadSettings(rs.getInt(1), rs.getInt(2), rs.getInt(3)),
                projects.user().id());
    }

    @Transactional
    public void update(int greenHours, int yellowHours, int orangeHours) {
        WorkloadSettings settings = new WorkloadSettings(
                Math.multiplyExact(greenHours, 60),
                Math.multiplyExact(yellowHours, 60),
                Math.multiplyExact(orangeHours, 60));
        db.update("""
                UPDATE users SET workload_green_minutes=?,workload_yellow_minutes=?,
                    workload_orange_minutes=?,updated_at=CURRENT_TIMESTAMP WHERE id=?
                """, settings.greenMinutes(), settings.yellowMinutes(), settings.orangeMinutes(),
                projects.user().id());
    }
}
