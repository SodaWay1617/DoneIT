package com.doneit.user.application;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class LearningService {
    public static final int TOPIC_COUNT = 8;
    private final JdbcTemplate db;

    public LearningService(JdbcTemplate db) {
        this.db = db;
    }

    public record Progress(int readMask, boolean dismissed) {
        public boolean read(int topic) { return (readMask & (1 << topic)) != 0; }
        public int count() { return Integer.bitCount(readMask); }
        public boolean complete() { return count() == TOPIC_COUNT; }
        public boolean invite() { return !dismissed && !complete(); }
        public int nextTopic() {
            for (int topic = 0; topic < TOPIC_COUNT; topic++) if (!read(topic)) return topic;
            return TOPIC_COUNT - 1;
        }
    }

    public Progress progress(String login) {
        return db.queryForObject("SELECT learning_read_mask, learning_dismissed FROM users WHERE login=?",
                (rs, row) -> new Progress(rs.getInt(1), rs.getBoolean(2)), login);
    }

    public void validateTopic(int topic) {
        if (topic < 0 || topic >= TOPIC_COUNT)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown learning topic");
    }

    @Transactional
    public void markRead(String login, int topic) {
        validateTopic(topic);
        // Update the bit in SQL so parallel tabs cannot overwrite another topic's progress.
        db.update("UPDATE users SET learning_read_mask = learning_read_mask | ? WHERE login=?", 1 << topic, login);
    }

    @Transactional
    public void dismiss(String login) {
        db.update("UPDATE users SET learning_dismissed=true WHERE login=?", login);
    }

    @Transactional
    public void restart(String login) {
        db.update("UPDATE users SET learning_read_mask=0, learning_dismissed=false WHERE login=?", login);
    }
}
