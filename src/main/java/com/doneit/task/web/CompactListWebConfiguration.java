package com.doneit.task.web;

import com.doneit.project.application.ProjectService;
import com.doneit.project.application.TaskVisibility;
import com.doneit.project.domain.Project;
import com.doneit.tag.application.TagService;
import com.doneit.task.application.view.DailyTaskEntryView;
import com.doneit.task.application.view.DailyTasksView;
import com.doneit.task.domain.PlanningType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalTime;
import java.util.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.UriComponentsBuilder;

@Configuration
public class CompactListWebConfiguration implements WebMvcConfigurer {
    private final TagService tags;
    private final ProjectService projects;
    private final JdbcTemplate db;
    public CompactListWebConfiguration(TagService tags,ProjectService projects,JdbcTemplate db) {
        this.tags=tags;this.projects=projects;this.db=db;
    }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        // postHandle runs in reverse order: project, planning and tag models must already exist.
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public void postHandle(HttpServletRequest request,HttpServletResponse response,Object handler,ModelAndView view) {
                if(view==null||!"tasks".equals(view.getViewName())||request.getUserPrincipal()==null) return;
                build(request,view);
            }
        }).order(-100);
    }
    @SuppressWarnings("unchecked")
    private void build(HttpServletRequest request,ModelAndView view) {
        var model=view.getModel();
        var options=tags.list();
        Set<Long> selected=new LinkedHashSet<>();
        try {
            String[] values=request.getParameterValues("tagId");
            if(values!=null) {
                if(values.length>20) throw new IllegalArgumentException();
                for(String value:values) if(!value.isBlank()) selected.add(Long.valueOf(value));
            }
        } catch(IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid tags"); }
        if(!options.stream().map(TagService.Tag::id).toList().containsAll(selected))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        List<DailyTaskEntryView> candidates=(List<DailyTaskEntryView>)model.getOrDefault("compactCandidates",List.of());
        List<Project> available=(List<Project>)model.getOrDefault("projects",List.of());
        Map<Long,String> names=new HashMap<>();
        available.forEach(p->names.put(p.id(),p.defaultProject()?"MAIN":p.name().split("___",2)[0]));
        Map<Long,List<TagService.Tag>> ordinaryTags=(Map<Long,List<TagService.Tag>>)model.getOrDefault("taskTags",Map.of());
        Map<Long,List<TagService.Tag>> regularTags=(Map<Long,List<TagService.Tag>>)model.getOrDefault("regularTaskTags",Map.of());
        Map<Long,List<String>> ordinaryPeople=(Map<Long,List<String>>)model.getOrDefault("taskAssignees",Map.of());
        Map<Long,List<String>> regularPeople=(Map<Long,List<String>>)model.getOrDefault("regularTaskAssignees",Map.of());
        Map<Long,LocalTime> ends=new HashMap<>();
        if(candidates.stream().anyMatch(e->e.task()!=null))
            db.query("SELECT t.id,t.end_time FROM tasks t WHERE t.end_time IS NOT NULL AND "+TaskVisibility.memberTasks("t",false),
                r->{ends.put(r.getLong("id"),r.getObject("end_time",LocalTime.class));},projects.user().id());
        List<CompactTaskRow> rows=new ArrayList<>();
        for(var entry:candidates) {
            var t=entry.task();var r=entry.regularTask();boolean recurring=r!=null;
            Long id=recurring?r.id():t.id();
            LocalTime start=recurring?r.occurrenceTime():t.plannedForAt()==null?null:t.plannedForAt().toLocalTime();
            LocalTime end=recurring?r.endTime():ends.get(id);
            Integer estimate=recurring?r.estimateMinutes():t.estimateMinutes();
            var row=new CompactTaskRow(id,recurring,recurring?r.title():t.title(),
                names.getOrDefault(recurring?r.projectId():t.projectId(),""),
                recurring?r.priority():t.priority(),recurring?r.planningType():t.planningType(),
                !recurring&&t.overdue(),recurring?null:t.plannedForAt()!=null?t.plannedForAt().toLocalDate():t.plannedDate(),
                start,estimate,CompactTaskRow.interval(start,end,estimate),CompactTaskRow.nextDays(start,end,estimate),
                (recurring?regularTags:ordinaryTags).getOrDefault(id,List.of()),
                (recurring?regularPeople:ordinaryPeople).getOrDefault(id,List.of()));
            if(selected.isEmpty()||row.tags().stream().anyMatch(tag->selected.contains(tag.id()))) rows.add(row);
        }
        Map<String,Long> counts=new LinkedHashMap<>();
        counts.put("ALL",(long)rows.size());
        for(var type:PlanningType.values()) counts.put(type.name(),rows.stream().filter(r->r.planningType()==type).count());
        var planning=(PlanningType)model.get("selectedPlanningType");
        model.put("compactRows",rows.stream().filter(r->planning==null||r.planningType()==planning).toList());
        model.put("compactCounts",counts); model.put("compactTotal",candidates.size());
        model.put("listTagOptions",options);model.put("selectedTagIds",selected);
        var uri=UriComponentsBuilder.fromPath(request.getRequestURI());
        if(model.get("daily") instanceof DailyTasksView daily && request.getParameter("date")!=null) uri.queryParam("date",daily.selectedDate());
        if(model.get("selectedProjectId")!=null) uri.queryParam("projectId",model.get("selectedProjectId"));
        if(planning!=null) uri.queryParam("planningType",planning.name());
        selected.forEach(id->uri.queryParam("tagId",id));
        model.put("compactReturnTo",uri.build().toUriString());
        model.put("compactResetTo",uri.replaceQueryParam("planningType").replaceQueryParam("tagId").build().toUriString());
    }
}
