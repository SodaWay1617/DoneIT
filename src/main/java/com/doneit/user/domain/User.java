package com.doneit.user.domain;

import java.time.LocalDateTime;

public record User(
        Long id,
        String login,
        String passwordHash,
        String displayName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean showProjectInTaskTitle
) {
    public User(Long id, String login, String passwordHash, String displayName,
                LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, login, passwordHash, displayName, createdAt, updatedAt, false);
    }
}
