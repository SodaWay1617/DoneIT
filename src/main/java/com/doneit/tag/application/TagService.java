package com.doneit.tag.application;

import com.doneit.project.application.ProjectService;
import com.doneit.project.application.TaskVisibility;
import com.doneit.task.domain.TaskStatus;
import java.time.YearMonth;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class TagService {
    private final JdbcTemplate db;
    private final ProjectService projects;

    public TagService(JdbcTemplate db, ProjectService projects) {
        this.db = db; this.projects = projects;
    }

    public record Tag(Long id, String name, long taskCount) {}
    public record Task(Long id, boolean recurring, String title, String project, TaskStatus status,
                       Integer estimateMinutes, long actualMinutes) {
        public String href() { return (recurring ? "/recurring/" : "/tasks/") + id + "/edit"; }
    }
    public record Detail(Tag tag, YearMonth month, List<Task> tasks) {
        public long actualMinutes() { return tasks.stream().mapToLong(Task::actualMinutes).sum(); }
        public long ordinaryCount() { return tasks.stream().filter(t -> !t.recurring()).count(); }
        public long recurringCount() { return tasks.stream().filter(Task::recurring).count(); }
    }

    public List<Tag> list() {
        Long user = projects.user().id();
        return db.query("""
                SELECT tag.id,tag.name,COUNT(link.tag_id) task_count FROM tags tag
                LEFT JOIN (
                    SELECT tt.tag_id FROM task_tags tt JOIN tasks t ON t.id=tt.task_id WHERE %s
                    UNION ALL
                    SELECT tt.tag_id FROM task_tags tt JOIN regular_tasks t ON t.id=tt.regular_task_id WHERE %s
                ) link ON link.tag_id=tag.id
                WHERE tag.user_id=? GROUP BY tag.id,tag.name ORDER BY LOWER(tag.name),tag.id
                """.formatted(TaskVisibility.memberTasks("t",false), TaskVisibility.memberTasks("t",true)),
                (r,n) -> new Tag(r.getLong("id"),r.getString("name"),r.getLong("task_count")),user,user,user);
    }

    public Tag get(Long id) {
        return db.query("SELECT id,name FROM tags WHERE id=? AND user_id=?",
                (r,n) -> new Tag(r.getLong("id"),r.getString("name"),0),id,projects.user().id())
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public Tag create(String value) {
        String name = TagNames.normalize(value);
        Long id = db.queryForObject("""
                INSERT INTO tags(user_id,name,name_key) VALUES(?,?,?)
                ON CONFLICT(user_id,name_key) DO UPDATE SET name=tags.name RETURNING id
                """,Long.class,projects.user().id(),name,TagNames.key(name));
        return get(id);
    }

    @Transactional
    public void rename(Long id, String value) {
        get(id);
        String name = TagNames.normalize(value);
        db.update("UPDATE tags SET name=?,name_key=? WHERE id=? AND user_id=?",
                name,TagNames.key(name),id,projects.user().id());
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        db.update("DELETE FROM tags WHERE id=? AND user_id=?",id,projects.user().id());
    }

    @Transactional
    public void replace(Long taskId, boolean regular, Collection<String> requested) {
        List<String> names = TagNames.normalize(requested);
        projects.requireTaskAccess(taskId,regular);
        Long user = projects.user().id();
        String column = regular ? "regular_task_id" : "task_id";
        // Serialize replacement of this user's labels on the same task across tabs.
        db.queryForObject("SELECT id FROM " + (regular ? "regular_tasks" : "tasks") + " WHERE id=? FOR UPDATE",
                Long.class,taskId);
        db.update("DELETE FROM task_tags tt USING tags tag WHERE tt.tag_id=tag.id AND tag.user_id=? AND tt."
                + column + "=?",user,taskId);
        for (String name : names) {
            Long tag = create(name).id();
            db.update("INSERT INTO task_tags(tag_id," + column + ") VALUES(?,?)",tag,taskId);
        }
    }

    public List<String> names(Long id, boolean regular) {
        projects.requireTaskAccess(id,regular);
        return db.query("SELECT tag.name FROM tags tag JOIN task_tags tt ON tt.tag_id=tag.id "
                + "WHERE tag.user_id=? AND tt." + (regular ? "regular_task_id" : "task_id")
                + "=? ORDER BY LOWER(tag.name),tag.id",(r,n)->r.getString(1),projects.user().id(),id);
    }

    public Map<Long,List<Tag>> visible(boolean regular) {
        String table = regular ? "regular_tasks" : "tasks";
        String column = regular ? "regular_task_id" : "task_id";
        Long user = projects.user().id();
        Map<Long,List<Tag>> result = new LinkedHashMap<>();
        db.query("SELECT t.id task_id,tag.id,tag.name FROM tags tag JOIN task_tags tt ON tt.tag_id=tag.id "
                + "JOIN " + table + " t ON t.id=tt." + column + " WHERE tag.user_id=? AND "
                + TaskVisibility.memberTasks("t",regular) + " ORDER BY LOWER(tag.name),tag.id",r -> {
                    result.computeIfAbsent(r.getLong("task_id"),ignored->new ArrayList<>())
                            .add(new Tag(r.getLong("id"),r.getString("name"),0));
                },user,user);
        return result;
    }

    public Detail detail(Long id, YearMonth month) {
        Tag tag = get(id);
        List<Task> tasks = new ArrayList<>(tasks(id,month,false));
        tasks.addAll(tasks(id,month,true));
        tasks.sort(Comparator.comparing(Task::status).thenComparing(Task::title,String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Task::recurring).thenComparing(Task::id));
        return new Detail(tag,month,List.copyOf(tasks));
    }

    private List<Task> tasks(Long tag, YearMonth month, boolean regular) {
        String table = regular ? "regular_tasks" : "tasks";
        String entries = regular ? "regular_task_time_entries" : "task_time_entries";
        String column = regular ? "regular_task_id" : "task_id";
        Long user = projects.user().id();
        String sql = "SELECT t.id,t.title,p.name project_name,t.status,t.estimate_minutes,"
                + "COALESCE((SELECT SUM(e.minutes) FROM " + entries + " e WHERE e." + column
                + "=t.id AND e.user_id=? AND e.entry_date>=? AND e.entry_date<?),0) actual "
                + "FROM " + table + " t JOIN projects p ON p.id=t.project_id "
                + "JOIN task_tags tt ON tt." + column + "=t.id JOIN tags tag ON tag.id=tt.tag_id "
                + "WHERE tag.id=? AND tag.user_id=? AND " + TaskVisibility.memberTasks("t",regular);
        return db.query(sql,(r,n)->new Task(r.getLong("id"),regular,r.getString("title"),r.getString("project_name"),
                        TaskStatus.valueOf(r.getString("status")),(Integer)r.getObject("estimate_minutes"),r.getLong("actual")),
                user,month.atDay(1),month.plusMonths(1).atDay(1),tag,user,user);
    }
}
