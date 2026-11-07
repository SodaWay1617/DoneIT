package com.doneit.user.application;

import com.doneit.project.application.ProjectService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
@Transactional(readOnly = true)
public class WipSettingsService {
    public record Settings(boolean enabled, int minutes) {
        public int hours() { return minutes / 60; }
        public int remainingMinutes() { return minutes % 60; }
    }
    private final JdbcTemplate db;
    private final ProjectService projects;
    public WipSettingsService(JdbcTemplate db, ProjectService projects) { this.db=db; this.projects=projects; }
    public Settings current() {
        return db.queryForObject("SELECT wip_enabled,wip_limit_minutes FROM users WHERE id=?",
                (rs,row)->new Settings(rs.getBoolean(1),rs.getInt(2)),projects.user().id());
    }
    @Transactional
    public void update(boolean enabled, int hours, int minutes) {
        long total=hours*60L+minutes;
        if(hours<0 || minutes<0 || minutes>59 || total>Integer.MAX_VALUE || enabled && total==0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid WIP limit");
        db.update("UPDATE users SET wip_enabled=?,wip_limit_minutes=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                enabled,(int)total,projects.user().id());
    }
}
