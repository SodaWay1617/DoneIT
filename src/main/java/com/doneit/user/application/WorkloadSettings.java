package com.doneit.user.application;

public record WorkloadSettings(int greenMinutes, int yellowMinutes, int orangeMinutes) {
    public WorkloadSettings {
        if (greenMinutes <= 0 || greenMinutes >= yellowMinutes || yellowMinutes >= orangeMinutes) {
            throw new IllegalArgumentException("Workload thresholds must be positive and increasing");
        }
    }

    public String level(int minutes) {
        if (minutes <= greenMinutes) return "green";
        if (minutes <= yellowMinutes) return "yellow";
        if (minutes <= orangeMinutes) return "orange";
        return "red";
    }

    public int greenHours() { return greenMinutes / 60; }
    public int yellowHours() { return yellowMinutes / 60; }
    public int orangeHours() { return orangeMinutes / 60; }
}
