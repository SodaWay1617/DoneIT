package com.doneit.task.web;

import com.doneit.task.application.WipConsent;
import com.doneit.task.application.WipService.Warning;
import com.doneit.task.domain.PlanningType;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class WipWebConsent implements WipConsent {
    public static class Required extends RuntimeException {
        private final List<Warning> warnings;
        private final String approval;
        Required(List<Warning> warnings,String approval) { super("WIP confirmation required");this.warnings=warnings;this.approval=approval; }
        public List<Warning> warnings() { return warnings; }
        public String approval() { return approval; }
    }
    private HttpServletRequest request() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a?a.getRequest():null;
    }
    public boolean interactive() {
        var r=request();
        return r!=null && "POST".equals(r.getMethod()) && r.getRequestURI().matches(
                "(?:/tasks(?:/\\d+(?:/(?:reschedule|start-today))?|/bulk-move-(?:to-tomorrow|overdue-to-today))?|/planning/tasks/\\d+/schedule)");
    }
    public PlanningType category(PlanningType value) {
        var r=request();
        if(r!=null && r.getRequestURI().matches("/tasks(?:/\\d+)?")
                && (r.getParameter("planningType")==null || r.getParameter("planningType").isBlank()))return PlanningType.OPTIONAL;
        return value;
    }
    public void confirm(List<Warning> warnings) {
        var r=request();String expected=signature(r,warnings);
        String actual=r.getParameter("wipApproval");
        if(actual==null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),actual.getBytes(StandardCharsets.UTF_8)))
            throw new Required(warnings,expected);
    }
    private String signature(HttpServletRequest request,List<Warning> warnings) {
        var session=request.getSession();byte[] key;
        synchronized(session) {
            key=(byte[])session.getAttribute("wip.signingKey");
            if(key==null) { key=new byte[32];new SecureRandom().nextBytes(key);session.setAttribute("wip.signingKey",key); }
        }
        try {
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));
            // Length-delimited values avoid ambiguous field combinations; every submitted field is bound.
            List<String> values=new ArrayList<>(List.of(request.getUserPrincipal().getName(),request.getRequestURI(),warnings.toString()));
            new TreeMap<>(request.getParameterMap()).forEach((name,entries)->{
                if(!Set.of("_csrf","wipApproval").contains(name)) {
                    values.add(name);values.add(Integer.toString(entries.length));values.addAll(Arrays.asList(entries));
                }
            });
            for(String value:values) {
                byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
                mac.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());mac.update(bytes);
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal());
        } catch(GeneralSecurityException e) { throw new IllegalStateException("WIP approval could not be prepared",e); }
    }
}
