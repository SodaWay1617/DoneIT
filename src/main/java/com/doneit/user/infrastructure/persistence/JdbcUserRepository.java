package com.doneit.user.infrastructure.persistence;

import com.doneit.user.domain.User;
import com.doneit.user.domain.UserRepository;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUserRepository implements UserRepository {

    private static final String FIND_ACTIVE_USER_SQL = """
            SELECT id, login, password_hash, display_name, created_at, updated_at
            FROM users
            ORDER BY id
            LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;
    private final UserRowMapper userRowMapper;

    public JdbcUserRepository(JdbcTemplate jdbcTemplate, UserRowMapper userRowMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRowMapper = userRowMapper;
    }

    @Override
    public Optional<User> findActiveUser() {
        return jdbcTemplate.query(FIND_ACTIVE_USER_SQL, userRowMapper)
                .stream()
                .findFirst();
    }
}
