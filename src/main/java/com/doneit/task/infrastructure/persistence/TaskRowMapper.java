package com.doneit.task.infrastructure.persistence;

import com.doneit.task.domain.Task;
import com.doneit.task.domain.TaskStatus;
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
        );
    }

    private static java.time.LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
