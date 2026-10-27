package com.doneit.mcp.application;

import com.doneit.mcp.infrastructure.*;
import com.doneit.mcp.security.McpIdentity;
import com.doneit.project.application.ProjectService;
import com.doneit.task.application.TaskApplicationService;
import com.doneit.task.application.command.*;
import com.doneit.task.domain.TaskStatus;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.util.*;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class McpOperations {
    private final ProjectService projects;
    private final TaskApplicationService tasks;
    private final McpTaskStore store;
    private final McpAudit audit;
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final Clock clock;
    public McpOperations(ProjectService projects,TaskApplicationService tasks,McpTaskStore store,McpAudit audit,
            JdbcTemplate db,ObjectMapper json,Clock clock) {
        this.projects=projects;this.tasks=tasks;this.store=store;this.audit=audit;this.db=db;this.json=json;this.clock=clock;
    }

    @Transactional
    public Object execute(McpIdentity who,String tool,JsonNode input) {
        boolean write=Set.of("create_task","update_task","close_task").contains(tool);
        if (write && !who.write()) throw new McpFailure("FORBIDDEN","This token is read-only");
        return switch(tool) {
            case "list_projects" -> {
                McpTaskInput.object(input,Set.of());
                var list=projects.list().stream().map(p->Map.of("id",p.id(),"name",p.defaultProject()?"MAIN":p.name().split("___",2)[0],"main",p.defaultProject())).toList();
                audit.record(who,tool,null,null,"OK",Map.of("count",list.size()));
                yield Map.of("projects",list,"timezone",clock.getZone().getId(),"today",LocalDate.now(clock));
            }
            case "list_tasks" -> {
                var page=store.list(input,clock);
                audit.record(who,tool,null,null,"OK",Map.of("count",page.tasks().size()));
                yield page;
            }
            case "get_task" -> {
                McpTaskInput.object(input,Set.of("taskId"));
                Long id=id(input);
                var card=store.card(store.get(id,false));
                audit.record(who,tool,null,id,"OK",Map.of());
                yield Map.of("task",card,"timezone",clock.getZone().getId(),"today",LocalDate.now(clock));
            }
            case "create_task" -> create(who,input);
            case "update_task" -> update(who,input,false);
            case "close_task" -> update(who,input,true);
            default -> throw McpFailure.invalid("Unknown tool");
        };
    }

    private Object create(McpIdentity who,JsonNode input) {
        McpTaskInput.object(input,Set.of("task","requestId"));
        String request=McpTaskInput.text(input,"requestId",null,false,100);
        if (request==null || !request.matches("[A-Za-z0-9._-]{1,100}")) throw McpFailure.invalid("requestId is required (letters, digits, dot, underscore or dash)");
        Long main=projects.list().stream().filter(p->p.defaultProject()).map(p->p.id()).findFirst().orElseThrow();
        var data=McpTaskInput.merge(input.get("task"),null,main);
        String hash=HexFormat.of().formatHex(com.doneit.mcp.security.McpTokenVerifier.digest(encode(data)));
        Long user=projects.user().id();
        int inserted=db.update("INSERT INTO mcp_requests(user_id,client_id,request_id,input_hash) VALUES(?,?,?,?) ON CONFLICT DO NOTHING",user,who.tokenId(),request,hash);
        if (inserted==0) {
            var previous=db.queryForMap("SELECT input_hash,result::text result FROM mcp_requests WHERE user_id=? AND client_id=? AND request_id=?",user,who.tokenId(),request);
            if (!hash.equals(previous.get("input_hash"))) throw new McpFailure("CONFLICT","requestId was already used with different fields");
            audit.record(who,"create_task",request,null,"REPLAY",Map.of());
            return decode((String)previous.get("result"));
        }
        requireProject(data.projectId());
        var command=new CreateTaskCommand(data.title(),data.description(),data.plannedForAt(),data.deadlineAt(),
                data.projectId(),data.plannedDate(),data.status()==TaskStatus.BACKLOG,data.priority(),data.status(),
                data.estimateMinutes(),data.planningType(),data.endTime());
        var created=data.status()==TaskStatus.BACKLOG?tasks.createBacklogTask(command):tasks.createTask(command);
        var saved=store.card(store.get(created.id(),false));
        var changes=difference(null,saved);
        var result=Map.of("task",saved,"changes",changes);
        db.update("UPDATE mcp_requests SET result=?::jsonb WHERE user_id=? AND client_id=? AND request_id=?",encode(result),user,who.tokenId(),request);
        audit.record(who,"create_task",request,created.id(),"OK",changes);
        return result;
    }

    private Object update(McpIdentity who,JsonNode input,boolean close) {
        McpTaskInput.object(input,close?Set.of("taskId","expectedRevision"):Set.of("taskId","expectedRevision","changes"));
        Long id=id(input);
        var expected=input.get("expectedRevision");
        if (expected==null || !expected.isIntegralNumber() || !expected.canConvertToLong() || expected.longValue()<0)
            throw McpFailure.invalid("expectedRevision is required");
        var current=store.get(id,true);
        if (current.revision()!=expected.longValue()) throw new McpFailure("CONFLICT","Task changed; get_task and review the changes before retrying");
        var before=store.card(current);
        if (close) {
            if (current.task().status()==TaskStatus.DONE) throw McpFailure.invalid("A completed task cannot be dismissed");
            if (current.task().status()!=TaskStatus.CLOSED) tasks.markTaskAsClosed(id);
        } else {
            var patch=input.get("changes");
            if (patch==null || !patch.isObject() || patch.isEmpty()) throw McpFailure.invalid("changes must not be empty");
            var data=McpTaskInput.merge(patch,current.task(),null);
            requireProject(data.projectId());
            if (!Objects.equals(data.projectId(),current.task().projectId())) store.requireAssigneesInProject(id,data.projectId());
            tasks.editTask(new EditTaskCommand(id,data.title(),data.description(),data.plannedForAt(),data.deadlineAt(),
                    data.projectId(),data.plannedDate(),data.status()==TaskStatus.BACKLOG,data.priority(),data.status(),
                    data.estimateMinutes(),current.task().spentMinutes(),data.planningType(),data.endTime()));
        }
        var saved=store.card(store.get(id,false));
        var changes=difference(before,saved);
        audit.record(who,close?"close_task":"update_task",null,id,"OK",changes);
        return Map.of("task",saved,"changes",changes);
    }

    private void requireProject(Long id) {if (!projects.canAccess(id)) throw new McpFailure("NOT_FOUND","Project not found");}
    private static Long id(JsonNode input) {
        Long id=McpTaskInput.number(input,"taskId",null,false);
        if (id==null) throw McpFailure.invalid("taskId is required");
        return id;
    }
    private ObjectNode difference(Object before,Object after) {
        JsonNode old=before==null?json.createObjectNode():json.valueToTree(before),saved=json.valueToTree(after);
        var changes=json.createObjectNode();
        saved.fields().forEachRemaining(entry -> {
            if (!Set.of("revision","updatedAt","createdAt").contains(entry.getKey()) && !Objects.equals(old.get(entry.getKey()),entry.getValue())) {
                var change=json.createObjectNode();change.set("before",old.has(entry.getKey())?old.get(entry.getKey()):json.nullNode());change.set("after",entry.getValue());
                changes.set(entry.getKey(),change);
            }
        });
        return changes;
    }
    private String encode(Object value) {try{return json.writeValueAsString(value);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Cannot encode MCP result");}}
    private JsonNode decode(String value) {try{return json.readTree(value);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Cannot read MCP result");}}
}
