package com.doneit.task.domain;

public enum TaskPriority {
    NONE("None"),
    LOW("Low"),
    NORMAL("Normal"),
    HIGH("High"),
    CRITICAL("Critical");

    private final String displayName;

    TaskPriority(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
