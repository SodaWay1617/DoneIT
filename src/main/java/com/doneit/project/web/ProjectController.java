package com.doneit.project.web;

import com.doneit.project.application.ProjectService;
import com.doneit.project.application.ProjectCodeConflictException;
import com.doneit.project.domain.TaskVisibilityMode;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProjectController {
 public record Draft(String name,String code,String description,TaskVisibilityMode visibility) {}
 private final ProjectService service;
 public ProjectController(ProjectService service){this.service=service;}
 @GetMapping("/projects") public String list(Model m){m.addAttribute("projects",service.list());m.addAttribute("defaultTaskVisibility",m.getAttribute("projectDraft") instanceof Draft draft ? draft.visibility() : TaskVisibilityMode.ALL_MEMBERS);return "projects";}
 @PostMapping("/projects") public String create(@RequestParam String name,@RequestParam String code,@RequestParam(required=false)String description,@RequestParam(defaultValue="ALL_MEMBERS")TaskVisibilityMode taskVisibility,RedirectAttributes flash){
  try { service.create(name,code,description,taskVisibility); }
  catch(ProjectCodeConflictException error) { conflict(flash,name,code,description,taskVisibility);return "redirect:/projects#new-project"; }
  return "redirect:/projects";
 }
 @GetMapping(\u0022/projects/{id}\u0022) public String detail(@PathVariable Long id,Model m){m.addAttribute(\u0022project\u0022,service.get(id));m.addAttribute(\u0022members\u0022,service.members(id));m.addAttribute(\u0022availableUsers\u0022,service.available(id));return \u0022project-detail\u0022;}
 @PostMapping("/projects/{id}") public String update(@PathVariable Long id,@RequestParam String name,@RequestParam String code,@RequestParam(required=false)String description,@RequestParam(required=false)TaskVisibilityMode taskVisibility,RedirectAttributes flash){
  TaskVisibilityMode visibility=taskVisibility==null?service.get(id).taskVisibility():taskVisibility;
  try { service.update(id,name,code,description,visibility); }
  catch(ProjectCodeConflictException error) { conflict(flash,name,code,description,visibility); }
  return "redirect:/projects/"+id;
 }
 private void conflict(RedirectAttributes flash,String name,String code,String description,TaskVisibilityMode visibility){
  flash.addFlashAttribute("projectCodeConflict",true);
  flash.addFlashAttribute("projectDraft",new Draft(name,code,description,visibility));
 }
 @PostMapping(\u0022/projects/{id}/delete\u0022) public String delete(@PathVariable Long id){service.delete(id);return \u0022redirect:/projects\u0022;}
 @PostMapping(\u0022/projects/{id}/members\u0022) public String add(@PathVariable Long id,@RequestParam Long userId){service.add(id,userId);return \u0022redirect:/projects/\u0022+id;}
 @PostMapping(\u0022/projects/{id}/members/{userId}/remove\u0022) public String remove(@PathVariable Long id,@PathVariable Long userId){service.remove(id,userId);return \u0022redirect:/projects/\u0022+id;}
 @PostMapping(\u0022/projects/{id}/leave\u0022) public String leave(@PathVariable Long id){service.leave(id);return \u0022redirect:/projects\u0022;}
}
