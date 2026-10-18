package com.doneit.user.web;

import com.doneit.config.DeviceRememberMeServices;
import com.doneit.user.application.RememberedDevicesService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class RememberedDevicesController {
    private final RememberedDevicesService devices;
    private final DeviceRememberMeServices remember;
    public RememberedDevicesController(RememberedDevicesService devices,DeviceRememberMeServices remember) {
        this.devices=devices;this.remember=remember;
    }
    @GetMapping("/settings/devices")
    public String list(Authentication authentication,HttpServletRequest request,Model model) {
        model.addAttribute("devices",devices.list(authentication.getName(),remember.series(request)));
        model.addAttribute("secureConnection",request.isSecure());
        return "devices";
    }
    @PostMapping("/settings/devices/{id}/revoke")
    public String revoke(@PathVariable long id,Authentication authentication,HttpServletRequest request,HttpServletResponse response) {
        boolean current=devices.list(authentication.getName(),remember.series(request)).stream().anyMatch(d->d.id()==id&&d.current());
        devices.revoke(authentication.getName(),id);
        if(current) {
            remember.logout(request,response,authentication);
            new SecurityContextLogoutHandler().logout(request,response,authentication);
            return "redirect:/login?logout";
        }
        return "redirect:/settings/devices";
    }
    @PostMapping("/settings/devices/revoke-others")
    public String revokeOthers(Authentication authentication,HttpServletRequest request) {
        devices.revokeOthers(authentication.getName(),remember.series(request),request.getSession().getId());
        return "redirect:/settings/devices";
    }
}
