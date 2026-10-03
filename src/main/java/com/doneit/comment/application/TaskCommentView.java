package com.doneit.comment.application;

import java.time.LocalDateTime;

public record TaskCommentView(Long id, String author, String body, LocalDateTime createdAt) {}
