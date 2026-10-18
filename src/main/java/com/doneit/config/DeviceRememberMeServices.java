package com.doneit.config;

import com.doneit.user.application.RememberedDevicesService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.authentication.rememberme.InvalidCookieException;

public class DeviceRememberMeServices extends PersistentTokenBasedRememberMeServices {
    public static final String SESSION_SERIES=DeviceRememberMeServices.class.getName()+".series";
    private final RememberedDevicesService devices;
    public DeviceRememberMeServices(String key,UserDetailsService users,PersistentTokenRepository tokens,RememberedDevicesService devices) {
        super(key,users,tokens);this.devices=devices;
        setTokenValiditySeconds(RememberedDevicesService.VALIDITY_SECONDS);
        setCookieCustomizer(cookie->cookie.setAttribute("SameSite","Lax"));
    }
    public String series(HttpServletRequest request) {
        var session=request.getSession(false);
        if(session!=null&&session.getAttribute(SESSION_SERIES) instanceof String bound) return bound;
        String cookie=extractRememberMeCookie(request);
        if(cookie==null) return null;
        try {String[] parts=decodeCookie(cookie);return parts.length==2?parts[0]:null;}
        catch(InvalidCookieException e) {return null;}
    }
    @Override public void loginSuccess(HttpServletRequest request,HttpServletResponse response,Authentication authentication) {
        String previous=series(request);
        request.getSession().removeAttribute(SESSION_SERIES);
        if(previous!=null) devices.revokeSeries(authentication.getName(),previous);
        if(!rememberMeRequested(request,getParameter())) cancelCookie(request,response);
        super.loginSuccess(request,response,authentication);
    }
    @Override public Authentication autoLogin(HttpServletRequest request,HttpServletResponse response) {
        try {return super.autoLogin(request,response);}
        catch(org.springframework.security.web.authentication.rememberme.CookieTheftException e) {
            cancelCookie(request,response);return null;
        }
    }
    @Override protected void setCookie(String[] tokens,int maxAge,HttpServletRequest request,HttpServletResponse response) {
        super.setCookie(tokens,maxAge,request,response);
        if(tokens.length==2) {
            // Auto-login binds its final session in RememberedDeviceSessionFilter, after authentication.
            var authentication=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if(authentication!=null) {
                devices.link(tokens[0],authentication.getName(),request.getSession().getId(),request.getHeader("User-Agent"));
                request.getSession().setAttribute(SESSION_SERIES,tokens[0]);
            }
        }
    }
    @Override public void logout(HttpServletRequest request,HttpServletResponse response,Authentication authentication) {
        String series=series(request);
        if(authentication!=null&&series!=null) devices.revokeSeries(authentication.getName(),series);
        cancelCookie(request,response);
    }
}
