package com.doneit.analytics.application;

public record ProjectTimeView(Long projectId, String projectName, int minutes, int sharePercent) {
    public String duration() { return minutes / 60 + "h " + minutes % 60 + "m"; }
}
