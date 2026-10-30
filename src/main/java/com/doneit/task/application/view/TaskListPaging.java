package com.doneit.task.application.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

public record TaskListPaging(String path, Long projectId, String planningType, int size, long total, long number) {
    public record PageNumber(Long number) {}
    public static TaskListPaging of(String path, Long project, String planning, int size, long total, long requested) {
        validate(size, requested);
        long pages = Math.max(1, total / size + (total % size == 0 ? 0 : 1));
        return new TaskListPaging(path, project, planning, size, total, Math.min(requested, pages));
    }
    public static void validate(int size, long page) {
        if (!Set.of(25, 50, 100).contains(size) || page < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
    public long pages() { return Math.max(1, total / size + (total % size == 0 ? 0 : 1)); }
    public long from() { return total == 0 ? 0 : (number - 1) * size + 1; }
    public long to() { return Math.min(number * size, total); }
    public boolean previous() { return number > 1; }
    public boolean next() { return number < pages(); }
    public String href(long page) { return link(projectId, planningType, size, page); }
    public String sizeHref(int value) { return link(projectId, planningType, value, 1); }
    public String projectHref(Long value) { return link(value, planningType, size, 1); }
    public String resetHref() { return link(null, null, size, 1); }
    private String link(Long project, String planning, int limit, long page) {
        var uri = UriComponentsBuilder.fromPath(path);
        if (project != null) uri.queryParam("projectId", project);
        if (planning != null) uri.queryParam("planningType", planning);
        if (limit != 25) uri.queryParam("size", limit);
        if (page > 1) uri.queryParam("page", page);
        return uri.build().toUriString();
    }
    public List<PageNumber> numbers() {
        var selected = new TreeSet<Long>(); selected.add(1L); selected.add(pages());
        for (long i = Math.max(1, number - 2); i <= Math.min(pages(), number + 2); i++) selected.add(i);
        List<PageNumber> result = new ArrayList<>(); long previous = 0;
        for (long value : selected) {
            if (value > previous + 1) result.add(new PageNumber(null));
            result.add(new PageNumber(value)); previous = value;
        }
        return List.copyOf(result);
    }
}
