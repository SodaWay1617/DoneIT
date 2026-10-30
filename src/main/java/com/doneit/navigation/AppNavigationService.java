package com.doneit.navigation;

import com.doneit.project.application.ProjectService;
import com.doneit.project.application.TaskVisibility;
import com.doneit.project.domain.Project;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Transactional(readOnly = true)
public class AppNavigationService {
    private final ProjectService projects;
    private final JdbcTemplate db;
    private final Clock clock;

    public AppNavigationService(ProjectService projects, JdbcTemplate db, Clock clock) {
        this.projects = projects;
        this.db = db;
        this.clock = clock;
    }

    public record Item(String key, String href, String icon, String label, long count, boolean active) {}
    public record ProjectItem(Long id, String name, String href, boolean active) {}
    public record Navigation(List<Item> main, List<Item> views, List<Item> analytics, List<Item> mobile,
                             List<Item> more, List<ProjectItem> projects, String allProjectsHref,
                             boolean allProjectsActive, boolean projectSectionActive, boolean moreActive,
                             String username, String englishHref, String russianHref, String section, String listHref) {}

    public Navigation build(HttpServletRequest request, List<Project> available, Long selectedProjectId) {
        var user = projects.user();
        Long selected = selectedProjectId != null && available.stream().anyMatch(p -> p.id().equals(selectedProjectId))
                ? selectedProjectId : null;
        String path = request.getRequestURI();
        String section = section(path);
        LocalDate date = date(request);
        String planning = request.getParameter("planningType");
        if (path.equals("/inbox")) planning = null;
        if (planning != null && !List.of("REQUIRED", "WANTED", "OPTIONAL").contains(planning)) planning = null;
        long[] counts = counts(user.id(), selected, planning);
        List<Item> main = List.of(
                item("today", link("/", selected, planning, null), "sun", "nav.today", 0, section),
                item("inbox", link("/inbox", selected, null, null), "inbox", "nav.inbox", counts[0], section),
                item("backlog", link("/backlog", selected, planning, null), "archive", "nav.backlog", counts[1], section),
                item("calendar", link("/calendar", selected, planning, date), "calendar-days", "nav.calendar", 0, section),
                item("schedule", link("/schedule", selected, planning, date), "calendar-clock", "schedule.title", 0, section),
                item("analytics", link("/analytics", selected, planning, date), "chart-no-axes-combined", "nav.analytics", 0, section));
        List<Item> views = List.of(
                item("kanban", link("/kanban", selected, planning, date), "columns-3", "nav.kanban", 0, section),
                item("finished", link("/finished", selected, planning, null), "circle-check", "nav.finished", 0, section));
        List<Item> analytics = List.of(
                new Item("plan-fact", link("/analytics", selected, planning, date), "chart-no-axes-combined", "analytics.planFactTitle", 0, path.equals("/analytics")),
                new Item("workload", link("/analytics/workload", selected, planning, null), "gauge", "workload.analytics.title", 0, path.equals("/analytics/workload")),
                new Item("reports", link("/reports", selected, planning, date), "file-chart-column", "reports.title", 0, path.equals("/reports")));
        Item projectItem = item("projects", "/projects", "folder", "project.title", 0, section);
        List<Item> mobile = List.of(main.get(0), main.get(1), main.get(3), projectItem);
        List<Item> more = new ArrayList<>(List.of(main.get(2), main.get(4), main.get(5), views.get(1), views.get(0)));
        more.addAll(analytics.subList(1, 3));
        more.add(item("settings", "/settings", "settings", "nav.settings", 0, section));
        final String selectedPlanning = planning;
        final String projectPath = path.equals("/inbox") || path.equals("/backlog") || path.equals("/finished") ? path : "/";
        List<ProjectItem> projectItems = available.stream().map(p -> new ProjectItem(p.id(),
                p.defaultProject() ? "MAIN" : p.name().split("___", 2)[0],
                path.equals("/finished") ? finishedProjectLink(request, p.id()) :
                    (path.equals("/inbox") || path.equals("/backlog") ? queueProjectLink(request, p.id(), selectedPlanning) : link(projectPath, p.id(), selectedPlanning, null)), p.id().equals(selected))).toList();
        String name = user.displayName() == null || user.displayName().isBlank() ? user.login() : user.displayName();
        boolean moreActive = mobile.stream().noneMatch(Item::active);
        return new Navigation(main, views, analytics, mobile, List.copyOf(more), projectItems,
                path.equals("/finished") ? finishedProjectLink(request, null) :
                    (path.equals("/inbox") || path.equals("/backlog") ? queueProjectLink(request, null, planning) : link(projectPath, null, planning, null)), selected == null, section.equals("projects"), moreActive, name,
                language(request, "en"), language(request, "ru"), section, link("/tasks", selected, planning, date));
    }

    private long[] counts(Long user, Long project, String planning) {
        String sql = "SELECT COUNT(*) FILTER (WHERE status='NEW' AND NOT recurring),"
                + "COUNT(*) FILTER (WHERE status IN ('BACKLOG','PAUSED') AND (?::text IS NULL OR planning_type=?)) FROM ("
                + "SELECT t.status,t.planning_type,false recurring FROM tasks t WHERE t.status IN ('NEW','BACKLOG','PAUSED') AND "
                + TaskVisibility.memberTasks("t", false) + " AND (?::bigint IS NULL OR t.project_id=?) UNION ALL "
                + "SELECT r.status,r.planning_type,true recurring FROM regular_tasks r WHERE r.status IN ('BACKLOG','PAUSED') AND "
                + TaskVisibility.memberTasks("r", true) + " AND (?::bigint IS NULL OR r.project_id=?)) visible_tasks";
        return db.queryForObject(sql, (rs, row) -> new long[]{rs.getLong(1), rs.getLong(2)},
                planning, planning, user, project, project, user, project, project);
    }

    private static Item item(String key, String href, String icon, String label, long count, String section) {
        return new Item(key, href, icon, label, count, key.equals(section));
    }

    private static String finishedProjectLink(HttpServletRequest request, Long projectId) {
        var uri = UriComponentsBuilder.fromPath("/finished");
        if (projectId != null) uri.queryParam("projectId", projectId);
        for (String key : List.of("status", "size", "planningType", "tagId")) {
            String[] values = request.getParameterValues(key);
            if (values != null) for (String value : values) uri.queryParam(key, value);
        }
        return uri.build().encode().toUriString();
    }

    private static String queueProjectLink(HttpServletRequest request, Long projectId, String planning) {
        var uri = UriComponentsBuilder.fromPath(request.getRequestURI());
        if (projectId != null) uri.queryParam("projectId", projectId);
        if (planning != null) uri.queryParam("planningType", planning);
        if (request.getParameter("size") != null) uri.queryParam("size", request.getParameter("size"));
        return uri.build().encode().toUriString();
    }

    private static String section(String path) {
        if (path.startsWith("/settings")) return "settings";
        if (path.startsWith("/projects")) return "projects";
        if (path.startsWith("/learn")) return "settings";
        if (path.startsWith("/analytics") || path.equals("/reports")) return "analytics";
        if (path.equals("/tags") || path.startsWith("/tags/")) return "tags";
        for (String section : List.of("inbox", "backlog", "calendar", "schedule", "kanban", "finished"))
            if (path.equals("/" + section)) return section;
        return "today";
    }

    private LocalDate date(HttpServletRequest request) {
        try {
            if (request.getParameter("date") != null) return LocalDate.parse(request.getParameter("date"));
            if (request.getParameter("month") != null) return YearMonth.parse(request.getParameter("month")).atDay(1);
        } catch (DateTimeParseException ignored) { /* Some pages do not interpret date parameters. */ }
        return LocalDate.now(clock);
    }

    private static String link(String path, Long project, String planning, LocalDate date) {
        var uri = UriComponentsBuilder.fromPath(path);
        if (project != null) uri.queryParam("projectId", project);
        if (planning != null) uri.queryParam("planningType", planning);
        if (date != null) uri.queryParam(path.equals("/calendar") ? "month" : "date", path.equals("/calendar") ? YearMonth.from(date) : date);
        return uri.build().toUriString();
    }

    private static String language(HttpServletRequest request, String language) {
        return UriComponentsBuilder.fromPath(request.getRequestURI()).query(request.getQueryString())
                .replaceQueryParam("lang", language).build().toUriString();
    }
}
