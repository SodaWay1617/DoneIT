package com.doneit.project.web;

import com.doneit.project.application.ProjectService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class ProjectController {
 private final ProjectService service;
 public ProjectController(ProjectService service){this.service=service;}
 @GetMapping(\u0022/projects\u0022) public String list(Model m){m.addAttribute(\u0022projects\u0022,service.list());return \u0022projects\u0022;}
 @PostMapping(\u0022/projects\u0022) public String create(@RequestParam String name,@RequestParam String code,@RequestParam(required=false)String description){service.create(name,code,description);return \u0022redirect:/projects\u0022;}
 @GetMapping(\u0022/projects/{id}\u0022) public String detail(@PathVariable Long id,Model m){m.addAttribute(\u0022project\u0022,service.get(id));m.addAttribute(\u0022members\u0022,service.members(id));m.addAttribute(\u0022availableUsers\u0022,service.available(id));return \u0022project-detail\u0022;}
 @PostMapping(\u0022/projects/{id}\u0022) public String update(@PathVariable Long id,@RequestParam String name,@RequestParam String code,@RequestParam(required=false)String description){service.update(id,name,code,description);return \u0022redirect:/projects/\u0022+id;}
 @PostMapping(\u0022/projects/{id}/delete\u0022) public String delete(@PathVariable Long id){service.delete(id);return \u0022redirect:/projects\u0022;}
 @PostMapping(\u0022/projects/{id}/members\u0022) public String add(@PathVariable Long id,@RequestParam Long userId){service.add(id,userId);return \u0022redirect:/projects/\u0022+id;}
 @PostMapping(\u0022/projects/{id}/members/{userId}/remove\u0022) public String remove(@PathVariable Long id,@PathVariable Long userId){service.remove(id,userId);return \u0022redirect:/projects/\u0022+id;}
 @PostMapping(\u0022/projects/{id}/leave\u0022) public String leave(@PathVariable Long id){service.leave(id);return \u0022redirect:/projects\u0022;}
}
