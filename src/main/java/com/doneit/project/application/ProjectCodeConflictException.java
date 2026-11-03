package com.doneit.project.application;

public class ProjectCodeConflictException extends RuntimeException {
    public ProjectCodeConflictException(Throwable cause) {
        super("Project code already exists for this owner", cause);
    }
}
