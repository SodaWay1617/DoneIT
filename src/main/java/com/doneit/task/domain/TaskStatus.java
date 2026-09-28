package com.doneit.task.domain;

public enum TaskStatus {
    NEW("NEW", 0),
    BACKLOG("BACKLOG", 1),
    PAUSED("PAUSED", 2),
    SPECIFICATION("SPECIFICATION", 3),
    TODO("TODO", 4),
    IN_PROGRESS("IN-PROGRESS", 5),
    DOCUMENTATION("DOCUMENTATION", 6),
    DONE("DONE", 7),
    CLOSED("CLOSED", 8);

    private final String displayName;
    private final int workflowOrder;

    TaskStatus(String displayName, int workflowOrder) {
        this.displayName = displayName;
        this.workflowOrder = workflowOrder;
    }

    public String displayName() {
        return displayName;
    }

    public int workflowOrder() {
        return workflowOrder;
    }

    public boolean isFinished() {
        return this == DONE || this == CLOSED;
    }

    public boolean isBacklog() {
        return this == BACKLOG;
    }

    public boolean isActiveFlow() {
        return !isFinished() && this != BACKLOG;
    }

    public static TaskStatus[] workflowValues() {
        return new TaskStatus[] {
                NEW, BACKLOG, PAUSED, SPECIFICATION, TODO,
                IN_PROGRESS, DOCUMENTATION, DONE, CLOSED
        };
    }
}
