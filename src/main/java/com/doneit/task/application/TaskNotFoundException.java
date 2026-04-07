package com.doneit.task.application;

public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(Long taskId) {
        super("Task was not found: " + taskId);
    }
}