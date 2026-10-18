package com.doneit.task.application;

import com.doneit.project.application.ProjectService;
import com.doneit.task.domain.TaskStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskChangeHistoryService {
    private final JdbcTemplate db;
    private final ProjectService projects;
    private final MessageSource messages;

    public TaskChangeHistoryService(JdbcTemplate db, ProjectService projects, MessageSource messages) {
        this.db = db;
        this.projects = projects;
        this.messages = messages;
    }

    public record Entry(LocalDateTime changedAt, String field, String before, String after) {}
    public record History(long total, long reschedules, List<Entry> entries) {
        public boolean truncated() { return total > entries.size(); }
    }

    public History get(Long id, boolean regular) {
        projects.requireTaskAccess(id, regular);
        String column = regular ? "regular_task_id" : "task_id";
        long[] counts = db.queryForObject("SELECT COUNT(*),COUNT(*) FILTER (WHERE field='PLANNED' "
                + "AND old_value IS NOT NULL AND new_value IS NOT NULL) FROM task_change_history WHERE " + column + "=?",
                (rs, row) -> new long[]{rs.getLong(1), rs.getLong(2)}, id);
        List<Entry> entries = db.query("SELECT changed_at,field,old_value,new_value FROM task_change_history WHERE "
                + column + "=? ORDER BY id DESC LIMIT 100", (rs, row) -> {
                    String field = rs.getString("field");
                    return new Entry(rs.getObject("changed_at", LocalDateTime.class), text("history.field." + field),
                            value(field, rs.getString("old_value")), value(field, rs.getString("new_value")));
                }, id);
        return new History(counts[0], counts[1], entries);
    }

    private String value(String field, String value) {
        if (value == null) return text("history.unset");
        if ("STATUS".equals(field)) return text("status." + TaskStatus.valueOf(value).name());
        if ("ESTIMATE".equals(field)) return messages.getMessage("history.minutes", new Object[]{value}, LocaleContextHolder.getLocale());
        return value;
    }

    private String text(String key) {
        return messages.getMessage(key, null, LocaleContextHolder.getLocale());
    }
}
