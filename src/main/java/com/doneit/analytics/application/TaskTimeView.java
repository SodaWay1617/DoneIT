package com.doneit.analytics.application;

public record TaskTimeView(Long taskId, boolean recurring, String title, String projectName,
                           int minutes, int sharePercent) {
    public String duration() { return minutes / 60 + "h " + minutes % 60 + "m"; }
}
