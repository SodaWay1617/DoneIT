package com.doneit.recurring.web;

import com.doneit.recurring.application.RegularTaskService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.*;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class RecurringViewAdvice {
 private final RegularTaskService service;
 public RecurringViewAdvice(RegularTaskService service){this.service=service;}
 @ModelAttribute public void projections(HttpServletRequest request,Model model){
  String path=request.getRequestURI(); if(!(path.equals("/")||path.equals("/tasks")||path.equals("/backlog")||path.equals("/inbox")||path.equals("/finished")||path.equals("/kanban")||path.equals("/calendar")))return;
  Long project=parseLong(request.getParameter("projectId"));
  LocalDate date=parseDate(request.getParameter("date"),LocalDate.now());
  var regularActive=service.activeFor(date,project);
  model.addAttribute("regularActive",regularActive);
  model.addAttribute("regularEstimateMinutes",regularActive.stream()
   .map(com.doneit.recurring.domain.RegularTask::estimateMinutes).filter(java.util.Objects::nonNull)
   .mapToInt(Integer::intValue).sum());
  model.addAttribute("regularInbox",service.inbox(project));
  model.addAttribute("regularBacklog",service.backlog(project));
  model.addAttribute("regularFinished",service.finished(project));
  model.addAttribute("regularKanban",service.kanbanFor(date,project));
  YearMonth month=parseMonth(request.getParameter("month"),YearMonth.now());
  LocalDate first=month.atDay(1),start=first.minusDays(first.getDayOfWeek().getValue()-1);
  model.addAttribute("regularCalendar",service.occurrences(start,start.plusDays(42),project));
  model.addAttribute("regularSelectedDate",date);
 }
 private static Long parseLong(String value){try{return value==null||value.isBlank()?null:Long.valueOf(value);}catch(Exception e){return null;}}
 private static LocalDate parseDate(String value,LocalDate fallback){try{return value==null||value.isBlank()?fallback:LocalDate.parse(value);}catch(Exception e){return fallback;}}
 private static YearMonth parseMonth(String value,YearMonth fallback){try{return value==null||value.isBlank()?fallback:YearMonth.parse(value);}catch(Exception e){return fallback;}}
}
