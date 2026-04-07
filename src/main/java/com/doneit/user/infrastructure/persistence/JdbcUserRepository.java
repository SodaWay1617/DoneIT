package com.doneit.user.infrastructure.persistence;

import com.doneit.user.domain.User;
import com.doneit.user.domain.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JdbcUserRepository implements UserRepository {

    private static final String BASE_SELECT = """
            SELECT id, login, password_hash, display_name, created_at, updated_at
            FROM users
            """;

    private final JdbcTemplate jdbcTemplate;
    private final UserRowMapper userRowMapper;

    public JdbcUserRepository(JdbcTemplate jdbcTemplate, UserRowMapper userRowMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRowMapper = userRowMapper;
    }

    @Override
    public Optional<User> findActiveUser() {
        return jdbcTemplate.query(
                BASE_SELECT + """
                        ORDER BY id
                        LIMIT 1
                        """,
                userRowMapper
        ).stream().findFirst();
    }

    @Override
    public Optional<User> findByLogin(String login) {
        return jdbcTemplate.query(
                BASE_SELECT + "WHERE login = ?",
                userRowMapper,
                login
        ).stream().findFirst();
    }
}