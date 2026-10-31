package com.doneit.mcp.web;

import com.doneit.mcp.application.McpTokenService;
import com.doneit.mcp.security.McpProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class McpTokenController {
    private final McpTokenService tokens;
    private final McpProperties properties;

    public McpTokenController(McpTokenService tokens, McpProperties properties) {
        this.tokens = tokens;
        this.properties = properties;
    }

    @GetMapping("/settings/mcp-tokens")
    public String list(Principal user, HttpServletRequest request, HttpServletResponse response, Model model) {
        protectResponse(response);
        model.addAttribute("mcpTokens", tokens.list(user.getName()));
        model.addAttribute("canManageTokens", request.isSecure() && properties.enabled());
        return "mcp-tokens";
    }

    @PostMapping("/settings/mcp-tokens")
    @ResponseBody
    public McpTokenService.Issued issue(Principal user, HttpServletRequest request, HttpServletResponse response,
                                      @RequestParam String name, @RequestParam(defaultValue = "21") int days,
                                      @RequestParam(defaultValue = "false") boolean allowWrite) {
        protectResponse(response);
        requireSecure(request);
        return tokens.issue(user.getName(), name, days, allowWrite);
    }

    @PostMapping("/settings/mcp-tokens/{id}/revoke")
    public String revoke(@PathVariable String id, Principal user, HttpServletRequest request, HttpServletResponse response) {
        protectResponse(response);
        requireSecure(request);
        tokens.revoke(user.getName(), id);
        return "redirect:/settings/mcp-tokens";
    }

    private void requireSecure(HttpServletRequest request) {
        if (!request.isSecure() || !properties.enabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private void protectResponse(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
    }
}
