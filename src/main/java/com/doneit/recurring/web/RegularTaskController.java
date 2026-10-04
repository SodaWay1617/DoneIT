package com.doneit.recurring.web;

import com.doneit.assignment.application.TaskAssignmentService;
import com.doneit.project.application.ProjectService;
import com.doneit.recurring.application.*;
import com.doneit.recurring.domain.RecurrenceType;
import com.doneit.task.domain.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RegularTaskController {
 private final RegularTaskService service; private final ProjectService projects; private final TaskAssignmentService assignments;
 public RegularTaskController(RegularTaskService service,ProjectService projects){this(service,projects,null);}
 @Autowired public RegularTaskController(RegularTaskService service,ProjectService projects,TaskAssignmentService assignments){this.service=service;this.projects=projects;this.assignments=assignments;}
 @GetMapping("/recurring/new") public String create(Model m){return page(m,new RegularTaskForm(),"Create recurring task","/recurring");}
 @PostMapping("/recurring") public String create(@Valid @ModelAttribute("form") RegularTaskForm f,BindingResult errors,Model m,RedirectAttributes flash){
  if(errors.hasErrors())return page(m,f,"Create recurring task","/recurring");
  try{service.create(f);}catch(IllegalArgumentException e){errors.reject("regular",e.getMessage());return page(m,f,"Create recurring task","/recurring");}
  flash.addFlashAttribute("flashMessage","Recurring task created.");return "redirect:"+redirect(f.getStatus());
 }
 @GetMapping("/recurring/{id}/edit") public String edit(@PathVariable Long id,Model m){
  if(assignments!=null){var a=assignments.assignmentForRegularTask(id);m.addAttribute("assignmentMembers",a.members());m.addAttribute("assignedUserIds",a.assignedUserIds());}
  return page(m,service.form(id),"Edit recurring task","/recurring/"+id);
 }
 @PostMapping("/recurring/{id}") public String edit(@PathVariable Long id,@Valid @ModelAttribute("form") RegularTaskForm f,BindingResult errors,Model m,RedirectAttributes flash){
  if(errors.hasErrors())return page(m,f,"Edit recurring task","/recurring/"+id);
  try{service.update(id,f);}catch(IllegalArgumentException e){errors.reject("regular",e.getMessage());return page(m,f,"Edit recurring task","/recurring/"+id);}
  flash.addFlashAttribute("flashMessage","Recurring task updated.");return "redirect:"+redirect(f.getStatus());
 }
 @PostMapping("/recurring/{id}/done-today") public String done(@PathVariable Long id,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,@RequestParam(required=false) Integer spentMinutes,@RequestParam(defaultValue="/") String redirectTo,RedirectAttributes flash){
  service.complete(id,date,spentMinutes);flash.addFlashAttribute("flashMessage","Recurring task completed for this day.");
  return "redirect:"+(redirectTo.startsWith("/")&&!redirectTo.startsWith("//")?redirectTo:"/");
 }
 @PostMapping("/recurring/{id}/skip-today") public String skipToday(@PathVariable Long id,@RequestParam(defaultValue="/") String redirectTo,RedirectAttributes flash){
  service.skipToday(id);flash.addFlashAttribute("flashMessage","Recurring task skipped for today.");
  return "redirect:"+(redirectTo.startsWith("/")&&!redirectTo.startsWith("//")?redirectTo:"/");
 }
 @PostMapping("/recurring/{id}/track-time") public String track(@PathVariable Long id,@RequestParam int minutes,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,@RequestParam(defaultValue="/") String redirectTo,RedirectAttributes flash){service.track(id,date,minutes);flash.addFlashAttribute("flashMessage","Time tracked.");return "redirect:"+(redirectTo.startsWith("/")&&!redirectTo.startsWith("//")?redirectTo:"/");}
 private String page(Model m,RegularTaskForm f,String title,String action){
  m.addAttribute("form",f);m.addAttribute("pageTitle",title);m.addAttribute("formAction",action);m.addAttribute("projects",projects.list());
  m.addAttribute("statuses",new TaskStatus[]{TaskStatus.NEW,TaskStatus.BACKLOG,TaskStatus.SPECIFICATION,TaskStatus.IN_PROGRESS,TaskStatus.DOCUMENTATION,TaskStatus.DONE,TaskStatus.CLOSED});
  m.addAttribute("priorities",TaskPriority.values());m.addAttribute("recurrences",RecurrenceType.values());return "recurring-form";
 }
 private static String redirect(TaskStatus status){if(status==TaskStatus.NEW)return "/inbox";if(status==TaskStatus.BACKLOG)return "/backlog";if(status.isFinished())return "/finished";return "/";}
}
