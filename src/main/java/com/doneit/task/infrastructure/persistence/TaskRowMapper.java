package com.doneit.task.infrastructure.persistence;

import com.doneit.task.domain.Task;
import com.doneit.task.domain.TaskStatus;
import com.doneit.task.domain.TaskPriority;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class TaskRowMapper implements RowMapper<Task> {

    @Override
    public Task mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Task(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("title"),
                rs.getString("description"),
                TaskStatus.valueOf(rs.getString("status")),
                toLocalDateTime(rs.getTimestamp("planned_for_at")),
                toLocalDateTime(rs.getTimestamp("deadline_at")),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime(),
                toLocalDateTime(rs.getTimestamp("completed_at")),
                toLocalDateTime(rs.getTimestamp("closed_at"))
                ,rs.getLong(\u0022project_id\u0022)
                ,rs.getLong(\u0022task_number\u0022)
                ,rs.getString(\u0022task_key\u0022)
                ,rs.getObject(\u0022planned_date\u0022, java.time.LocalDate.class)
                ,TaskPriority.valueOf(rs.getString("priority"))
                ,(Integer) rs.getObject("estimate_minutes")
                ,rs.getInt("spent_minutes")
        );
    }

    private static java.time.LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
