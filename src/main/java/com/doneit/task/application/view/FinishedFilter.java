package com.doneit.task.application.view;

import java.util.ArrayList;
import java.util.List;
import org.springframework.web.util.UriComponentsBuilder;

public record FinishedFilter(Long projectId, List<Long> tagIds, String status, int size, String planningType) {
    public FinishedFilter { tagIds = List.copyOf(tagIds); }
    public String href(long page) { return link(projectId, tagIds, status, size, page); }
    public String statusHref(String value) { return link(projectId, tagIds, value, size, 1); }
    public String projectHref(Long value) { return link(value, tagIds, status, size, 1); }
    public String sizeHref(int value) { return link(projectId, tagIds, status, value, 1); }
    public boolean tagged(Long id) { return tagIds.contains(id); }
    public boolean active() { return projectId != null || !tagIds.isEmpty() || !status.equals("ALL") || planningType != null; }
    public String tagHref(Long value) {
        var selected = new ArrayList<>(tagIds);
        if (!selected.remove(value)) selected.add(value);
        return link(projectId, selected, status, size, 1);
    }
    private String link(Long project, List<Long> tags, String state, int limit, long page) {
        var uri = UriComponentsBuilder.fromPath("/finished");
        if (project != null) uri.queryParam("projectId", project);
        tags.forEach(id -> uri.queryParam("tagId", id));
        if (!"ALL".equals(state)) uri.queryParam("status", state);
        if (planningType != null) uri.queryParam("planningType", planningType);
        if (limit != 25) uri.queryParam("size", limit);
        if (page > 1) uri.queryParam("page", page);
        return uri.build().toUriString();
    }
}
