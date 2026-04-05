package com.doneit.user.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.doneit.user.domain.User;
import com.doneit.user.domain.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "spring.main.lazy-initialization=true")
class JdbcUserRepositoryTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findsSingleActiveUserByLowestId() {
        jdbcTemplate.update("DELETE FROM tasks");
        jdbcTemplate.update("DELETE FROM users");

        jdbcTemplate.update(
                """
                INSERT INTO users (login, password_hash, display_name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?), (?, ?, ?, ?, ?)
                """,
                "alpha",
                "hash-1",
                "Alpha User",
                LocalDateTime.of(2026, 4, 5, 12, 0),
                LocalDateTime.of(2026, 4, 5, 12, 0),
                "beta",
                "hash-2",
                "Beta User",
                LocalDateTime.of(2026, 4, 5, 12, 5),
                LocalDateTime.of(2026, 4, 5, 12, 5)
        );

        Optional<User> activeUser = userRepository.findActiveUser();

        assertTrue(activeUser.isPresent());
        assertEquals("alpha", activeUser.get().login());
        assertEquals("Alpha User", activeUser.get().displayName());
    }
}
