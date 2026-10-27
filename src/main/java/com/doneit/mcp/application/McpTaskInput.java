package com.doneit.mcp.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.doneit.task.domain.*;
import java.time.*;
import java.util.*;

/** Explicit PATCH semantics: a missing field preserves its value; null clears nullable fields only. */
public record McpTaskInput(String title,String description,Long projectId,TaskStatus status,TaskPriority priority,
        PlanningType planningType,LocalDate plannedDate,LocalDateTime plannedForAt,LocalDateTime deadlineAt,
        Integer estimateMinutes,LocalTime endTime) {
    public static final Set<String> FIELDS=Set.of("title","description","projectId","status","priority","planningType",
            "plannedDate","plannedForAt","deadlineAt","estimateMinutes","endTime");

    public static McpTaskInput merge(JsonNode fields,Task old,Long mainProject) {
        object(fields,FIELDS);
        String title=text(fields,"title",old==null?null:old.title(),false,255);
        if (title==null || title.isBlank()) throw McpFailure.invalid("title must not be blank");
        String description=text(fields,"description",old==null?null:old.description(),true,20000);
        Long project=number(fields,"projectId",old==null?mainProject:old.projectId(),false);
        TaskStatus status=enumeration(fields,"status",old==null?TaskStatus.NEW:old.status(),TaskStatus.class);
        TaskPriority priority=enumeration(fields,"priority",old==null?TaskPriority.NONE:old.priority(),TaskPriority.class);
        PlanningType planning=enumeration(fields,"planningType",old==null?PlanningType.REQUIRED:old.planningType(),PlanningType.class);
        LocalDate date=parse(fields,"plannedDate",old==null?null:old.plannedDate(),LocalDate::parse);
        LocalDateTime start=parse(fields,"plannedForAt",old==null?null:old.plannedForAt(),LocalDateTime::parse);
        LocalDateTime deadline=parse(fields,"deadlineAt",old==null?null:old.deadlineAt(),LocalDateTime::parse);
        LocalTime end=parse(fields,"endTime",old==null?null:old.endTime(),LocalTime::parse);
        Long estimate=number(fields,"estimateMinutes",old==null||old.estimateMinutes()==null?null:old.estimateMinutes().longValue(),true);
        if (estimate!=null && estimate>525600) throw McpFailure.invalid("estimateMinutes must not exceed 525600");
        if (status==TaskStatus.BACKLOG) {
            if ((fields.hasNonNull("plannedDate") || fields.hasNonNull("plannedForAt") || fields.hasNonNull("endTime")))
                throw McpFailure.invalid("Backlog tasks cannot have a planned date or time");
            date=null; start=null; end=null;
        }
        if (date!=null && start!=null) throw McpFailure.invalid("Choose plannedDate or plannedForAt; explicitly clear the other field when switching");
        TaskInterval.validate(start==null?null:start.toLocalTime(),end);
        if ((fields.hasNonNull("plannedForAt") && (start.getSecond()!=0 || start.getNano()!=0))
                || (fields.hasNonNull("endTime") && (end.getSecond()!=0 || end.getNano()!=0)))
            throw McpFailure.invalid("plannedForAt and endTime require minute precision");
        if (end!=null && (old==null || fields.has("endTime") || fields.has("plannedForAt") || fields.has("estimateMinutes"))) {
            long duration=TaskInterval.minutes(start.toLocalTime(),end);
            if (fields.hasNonNull("estimateMinutes") && !Objects.equals(estimate,duration))
                throw McpFailure.invalid("estimateMinutes must match the explicit time interval");
            estimate=duration;
        }
        if (old==null && status.isFinished()) throw McpFailure.invalid("Create an unfinished task first");
        return new McpTaskInput(title.trim(),description,project,status,priority,planning,date,start,deadline,
                estimate==null?null:estimate.intValue(),end);
    }

    public static void object(JsonNode value,Set<String> allowed) {
        if (value==null || !value.isObject()) throw McpFailure.invalid("Expected an object");
        value.fieldNames().forEachRemaining(key -> { if (!allowed.contains(key)) throw McpFailure.invalid("Unknown field: "+key); });
    }
    public static String text(JsonNode object,String name,String fallback,boolean nullable,int maximum) {
        if (!object.has(name)) return fallback;
        var value=object.get(name);
        if (nullable && value.isNull()) return null;
        if (!value.isTextual() || value.textValue().length()>maximum) throw McpFailure.invalid("Invalid "+name);
        return value.textValue();
    }
    public static Long number(JsonNode object,String name,Long fallback,boolean nullable) {
        if (!object.has(name)) return fallback;
        var value=object.get(name);
        if (nullable && value.isNull()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue()<=0) throw McpFailure.invalid("Invalid "+name);
        return value.longValue();
    }
    private static <T extends Enum<T>> T enumeration(JsonNode object,String name,T fallback,Class<T> type) {
        String value=text(object,name,fallback.name(),false,32);
        try { return Enum.valueOf(type,value); } catch (IllegalArgumentException e) { throw McpFailure.invalid("Invalid "+name); }
    }
    private static <T> T parse(JsonNode object,String name,T fallback,java.util.function.Function<String,T> parser) {
        String value=text(object,name,null,true,40);
        if (!object.has(name)) return fallback;
        if (value==null) return null;
        try { return parser.apply(value); } catch (java.time.format.DateTimeParseException e) { throw McpFailure.invalid("Invalid ISO date/time in "+name); }
    }
}
