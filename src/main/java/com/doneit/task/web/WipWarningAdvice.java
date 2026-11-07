package com.doneit.task.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;

@ControllerAdvice
public class WipWarningAdvice {
    private final ObjectMapper json;
    public WipWarningAdvice(ObjectMapper json) { this.json=json; }
    @ExceptionHandler(WipWebConsent.Required.class)
    public ModelAndView warning(WipWebConsent.Required required,HttpServletRequest request,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        if("true".equals(request.getHeader("X-DoneIt-Wip"))) {
            var result=new ModelAndView(new MappingJackson2JsonView(json),Map.of("kind","wip","approval",required.approval(),"days",required.warnings()));
            result.setStatus(HttpStatus.CONFLICT);return result;
        }
        var model=new ModelAndView("wip-confirm");
        model.addObject("wipDays",required.warnings());model.addObject("wipApproval",required.approval());
        model.addObject("wipAction",request.getRequestURI());
        model.addObject("wipEditor",request.getRequestURI().matches("/tasks(?:/\\d+)?"));
        Map<String,String[]> fields=new LinkedHashMap<>(request.getParameterMap());
        fields.remove("wipApproval");fields.remove("_csrf");model.addObject("wipFields",fields);
        model.setStatus(HttpStatus.CONFLICT);
        return model;
    }
}
