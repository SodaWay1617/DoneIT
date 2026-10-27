package com.doneit.mcp.infrastructure;

import com.doneit.project.application.*;
import com.doneit.task.domain.*;
import com.doneit.task.infrastructure.persistence.TaskRowMapper;
import com.doneit.mcp.application.McpFailure;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class McpTaskStore {
    private final JdbcTemplate db;
    private final ProjectService projects;
    private final TaskRowMapper mapper;
    public McpTaskStore(JdbcTemplate db,ProjectService projects,TaskRowMapper mapper) {
        this.db=db; this.projects=projects; this.mapper=mapper;
    }
    public record State(Task task,long revision) {}
    public record Tag(Long id,String name) {}
    public record Card(Long id,String code,String title,String description,Long projectId,String project,TaskStatus status,
            TaskPriority priority,PlanningType planningType,LocalDate plannedDate,LocalDateTime plannedForAt,
            LocalDateTime deadlineAt,Integer estimateMinutes,int spentMinutes,LocalTime endTime,
            LocalDateTime createdAt,LocalDateTime updatedAt,long revision,List<Tag> tags,List<String> assignees) {}
    public record Page(List<Card> tasks,Long nextCursor,String timezone,LocalDate today) {}

    public State get(Long id,boolean lock) {
        if (id==null || id<=0) throw McpFailure.invalid("taskId is required");
        return db.query("SELECT t.* FROM tasks t WHERE t.id=? AND "+TaskVisibility.memberTasks("t",false)+(lock?" FOR UPDATE OF t":""),
                (r,n) -> new State(mapper.mapRow(r,n),r.getLong("revision")),id,projects.user().id())
                .stream().findFirst().orElseThrow(() -> new McpFailure("NOT_FOUND","Task not found"));
    }

    public Page list(JsonNode input,Clock clock) {
        Set<String> allowed=Set.of("query","projectId","statuses","plannedFrom","plannedTo","cursor","limit");
        com.doneit.mcp.application.McpTaskInput.object(input,allowed);
        Long project=com.doneit.mcp.application.McpTaskInput.number(input,"projectId",null,true);
        if (project!=null && !projects.canAccess(project)) throw new McpFailure("NOT_FOUND","Project not found");
        String query=com.doneit.mcp.application.McpTaskInput.text(input,"query",null,true,200);
        Long cursor=com.doneit.mcp.application.McpTaskInput.number(input,"cursor",null,true);
        long limit=com.doneit.mcp.application.McpTaskInput.number(input,"limit",20L,false);
        if (limit>100) throw McpFailure.invalid("limit must be between 1 and 100");
        StringBuilder sql=new StringBuilder("SELECT t.* FROM tasks t WHERE "+TaskVisibility.memberTasks("t",false));
        List<Object> args=new ArrayList<>(); args.add(projects.user().id());
        if (project!=null) { sql.append(" AND t.project_id=?"); args.add(project); }
        if (query!=null && !query.isBlank()) { sql.append(" AND POSITION(LOWER(?) IN LOWER(t.title || ' ' || COALESCE(t.description,'')))>0"); args.add(query); }
        if (cursor!=null) { sql.append(" AND t.id<?"); args.add(cursor); }
        if (input.has("statuses")) {
            var statuses=input.get("statuses");
            if (!statuses.isArray() || statuses.isEmpty() || statuses.size()>TaskStatus.values().length) throw McpFailure.invalid("Invalid statuses");
            sql.append(" AND t.status IN (").append(String.join(",",Collections.nCopies(statuses.size(),"?"))).append(")");
            for (var status:statuses) {
                if (!status.isTextual()) throw McpFailure.invalid("Invalid status");
                try { args.add(TaskStatus.valueOf(status.textValue()).name()); } catch (IllegalArgumentException e) { throw McpFailure.invalid("Invalid status"); }
            }
        } else sql.append(" AND t.status NOT IN ('DONE','CLOSED')");
        LocalDate from=date(input,"plannedFrom"),to=date(input,"plannedTo");
        if (from!=null && to!=null && from.isAfter(to)) throw McpFailure.invalid("plannedFrom must not be after plannedTo");
        if (from!=null) { sql.append(" AND COALESCE(t.planned_date,t.planned_for_at::date)>=?"); args.add(from); }
        if (to!=null) { sql.append(" AND COALESCE(t.planned_date,t.planned_for_at::date)<=?"); args.add(to); }
        sql.append(" ORDER BY t.id DESC LIMIT ?"); args.add(limit+1);
        var rows=db.query(sql.toString(),(r,n)->new State(mapper.mapRow(r,n),r.getLong("revision")),args.toArray());
        boolean more=rows.size()>limit;
        var page=rows.subList(0,(int)Math.min(limit,rows.size()));
        return new Page(page.stream().map(this::card).toList(),more?page.getLast().task().id():null,clock.getZone().getId(),LocalDate.now(clock));
    }

    public Card card(State state) {
        var t=state.task();
        String project=projects.list().stream().filter(p->p.id().equals(t.projectId()))
                .map(p->p.defaultProject()?"MAIN":p.name().split("___",2)[0]).findFirst().orElse(null);
        List<Tag> tags=db.query("SELECT tag.id,tag.name FROM task_tags tt JOIN tags tag ON tag.id=tt.tag_id WHERE tt.task_id=? AND tag.user_id=? ORDER BY LOWER(tag.name),tag.id",
                (r,n)->new Tag(r.getLong("id"),r.getString("name")),t.id(),projects.user().id());
        List<String> people=db.query("SELECT u.login FROM task_assignees a JOIN users u ON u.id=a.user_id JOIN project_members m ON m.user_id=a.user_id AND m.project_id=? WHERE a.task_id=? ORDER BY u.login",
                (r,n)->r.getString(1),t.projectId(),t.id());
        String code=com.doneit.task.application.view.TaskListItemView.from(t,t.createdAt().toLocalDate(),false).taskCode();
        return new Card(t.id(),code,t.title(),t.description(),t.projectId(),project,t.status(),t.priority(),t.planningType(),
                t.plannedDate(),t.plannedForAt(),t.deadlineAt(),t.estimateMinutes(),t.spentMinutes(),t.endTime(),t.createdAt(),t.updatedAt(),state.revision(),tags,people);
    }

    public void requireAssigneesInProject(Long task,Long project) {
        Integer hierarchy=db.queryForObject("SELECT COUNT(*) FROM tasks WHERE (id=? AND parent_task_id IS NOT NULL) OR parent_task_id=?",Integer.class,task,task);
        if (hierarchy!=null && hierarchy>0) throw McpFailure.invalid("Move tasks with parent/child relationships through the standard editor");
        Integer missing=db.queryForObject("SELECT COUNT(*) FROM task_assignees a WHERE a.task_id=? AND NOT EXISTS (SELECT 1 FROM project_members m WHERE m.project_id=? AND m.user_id=a.user_id)",Integer.class,task,project);
        if (missing!=null && missing>0) throw McpFailure.invalid("The destination project must contain all task assignees");
    }

    private static LocalDate date(JsonNode input,String key) {
        String value=com.doneit.mcp.application.McpTaskInput.text(input,key,null,true,10);
        try { return value==null?null:LocalDate.parse(value); } catch (java.time.format.DateTimeParseException e) { throw McpFailure.invalid("Invalid ISO date in "+key); }
    }
}
