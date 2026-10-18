package com.doneit.config;

import com.doneit.user.application.RememberedDevicesService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

public class RememberedDeviceSessionFilter extends OncePerRequestFilter {
    private final DeviceRememberMeServices remember;
    private final RememberedDevicesService devices;
    private final SessionRegistry sessions;
    public RememberedDeviceSessionFilter(DeviceRememberMeServices remember,RememberedDevicesService devices,SessionRegistry sessions) {
        this.remember=remember;this.devices=devices;this.sessions=sessions;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication!=null&&authentication.isAuthenticated()&&!(authentication instanceof AnonymousAuthenticationToken)) {
            var session=request.getSession();
            String bound=(String)session.getAttribute(DeviceRememberMeServices.SESSION_SERIES);
            if(bound!=null&&!devices.valid(authentication.getName(),bound)) {
                remember.logout(request,response,authentication);
                new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler().logout(request,response,authentication);
                response.sendRedirect(request.getContextPath()+"/login?expired");return;
            }
            if(sessions.getSessionInformation(session.getId())==null) sessions.registerNewSession(session.getId(),authentication.getPrincipal());
            String series=remember.series(request);
            if(series!=null&&devices.valid(authentication.getName(),series)) {
                devices.link(series,authentication.getName(),session.getId(),request.getHeader("User-Agent"));
                session.setAttribute(DeviceRememberMeServices.SESSION_SERIES,series);
            }
        }
        chain.doFilter(request,response);
    }
}
