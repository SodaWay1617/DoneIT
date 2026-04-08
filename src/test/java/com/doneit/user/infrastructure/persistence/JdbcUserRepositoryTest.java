package com.doneit.user.infrastructure.persistence;

import com.doneit.support.IntegrationTestSupport;
import com.doneit.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = "spring.main.lazy-initialization=true")
class JdbcUserRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private JdbcUserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM tasks");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("""
                INSERT INTO users (login, password_hash, display_name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?), (?, ?, ?, ?, ?)
                """,
                "doneit",
                "hash-1",
                "DoneIt",
                LocalDateTime.of(2026, 4, 1, 10, 0),
                LocalDateTime.of(2026, 4, 1, 10, 0),
                "backup",
                "hash-2",
                "Backup",
                LocalDateTime.of(2026, 4, 1, 11, 0),
                LocalDateTime.of(2026, 4, 1, 11, 0)
        );
    }

    @Test
    void findsSingleActiveUserByLowestId() {
        Optional<User> activeUser = userRepository.findActiveUser();

        assertTrue(activeUser.isPresent());
        assertEquals("doneit", activeUser.get().login());
    }

    @Test
    void findsUserByLogin() {
        Optional<User> user = userRepository.findByLogin("backup");

        assertTrue(user.isPresent());
        assertEquals("Backup", user.get().displayName());
    }
}
