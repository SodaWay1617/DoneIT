package com.doneit.mcp.security;

import com.doneit.user.domain.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.net.URI;
import java.time.Clock;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

final class McpBearerFilter extends OncePerRequestFilter {
    private final McpProperties properties;
    private final McpTokenVerifier tokens;
    private final UserRepository users;
    private final Clock clock;
    private final Map<String, Session> sessions = new LinkedHashMap<>();
    private final Map<String, long[]> windows = new HashMap<>();
    private record Session(McpIdentity identity, long created) {}

    McpBearerFilter(McpProperties properties, McpTokenVerifier tokens, UserRepository users, Clock clock) {
        this.properties=properties; this.tokens=tokens; this.users=users; this.clock=clock;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!properties.enabled()) { reject(response,404); return; }
        if (!"/mcp".equals(request.getRequestURI().substring(request.getContextPath().length()))) { reject(response,404); return; }
        String host;
        try { host=URI.create("http://"+request.getHeader("Host")).getHost(); }
        catch (IllegalArgumentException e) { reject(response,403); return; }
        String origin=request.getHeader("Origin");
        if (host==null || properties.allowedHosts().stream().noneMatch(host::equalsIgnoreCase)
                || (origin!=null && !properties.allowedOrigins().contains(origin))) { reject(response,403); return; }
        if (!allow("global", 600)) { reject(response,429); return; }
        var identity=tokens.verify(request.getHeader("Authorization")).orElse(null);
        if (identity==null || users.findByLogin(identity.username()).isEmpty()) {
            response.setHeader("WWW-Authenticate", "Bearer realm=\"doneit-mcp\""); reject(response,401); return;
        }
        if (!allow("token:"+identity.tokenId(), properties.requestsPerMinute())) { reject(response,429); return; }
        String sessionId=request.getHeader("Mcp-Session-Id");
        synchronized (sessions) {
            sessions.values().removeIf(s -> clock.millis()-s.created()>86_400_000L);
            if (sessionId!=null) {
                Session session=sessions.get(sessionId);
                if (session==null || !session.identity().equals(identity)) { reject(response,404); return; }
            }
        }
        HttpServletRequest forwarded=request;
        if ("POST".equals(request.getMethod())) {
            if (request.getContentLengthLong()>properties.maxRequestBytes()) { reject(response,413); return; }
            byte[] body=request.getInputStream().readNBytes(properties.maxRequestBytes()+1);
            if (body.length>properties.maxRequestBytes()) { reject(response,413); return; }
            forwarded=new HttpServletRequestWrapper(request) {
                @Override public ServletInputStream getInputStream() {
                    var input=new ByteArrayInputStream(body);
                    return new ServletInputStream() {
                        @Override public int read() { return input.read(); }
                        @Override public boolean isFinished() { return input.available()==0; }
                        @Override public boolean isReady() { return true; }
                        @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                    };
                }
                @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8)); }
            };
        }
        forwarded.setAttribute(McpIdentity.ATTRIBUTE, identity);
        var previous=SecurityContextHolder.getContext();
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(identity, null, List.of(new SimpleGrantedAuthority("DONEIT_MCP"))));
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(forwarded, new HttpServletResponseWrapper(response) {
                @Override public void setHeader(String name, String value) {
                    if ("Mcp-Session-Id".equalsIgnoreCase(name) && value!=null) synchronized (sessions) {
                        if (sessions.size()>=256) sessions.remove(sessions.keySet().iterator().next());
                        sessions.put(value, new Session(identity, clock.millis()));
                    }
                    super.setHeader(name,value);
                }
                @Override public void addHeader(String name,String value) {
                    if ("Mcp-Session-Id".equalsIgnoreCase(name)) setHeader(name,value); else super.addHeader(name,value);
                }
            });
            if ("DELETE".equals(request.getMethod()) && sessionId!=null && response.getStatus()<400)
                synchronized (sessions) { sessions.remove(sessionId); }
        } finally { SecurityContextHolder.setContext(previous); }
    }

    private synchronized boolean allow(String key,int maximum) {
        long minute=clock.millis()/60_000;
        long[] window=windows.computeIfAbsent(key,k -> new long[]{minute,0});
        if (window[0]!=minute) { window[0]=minute; window[1]=0; }
        return ++window[1]<=maximum;
    }
    static void reject(HttpServletResponse response,int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":"+status+"}");
    }
}
