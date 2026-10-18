package com.doneit.user.application;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RememberedDevicesService {
    public static final int VALIDITY_SECONDS=90*24*60*60;
    private final JdbcTemplate db;
    private final SessionRegistry sessions;
    public RememberedDevicesService(JdbcTemplate db,SessionRegistry sessions) {this.db=db;this.sessions=sessions;}
    public List<Device> list(String username,String currentSeries) {
        return db.query("SELECT id,series,device_name,last_used FROM persistent_logins WHERE username=? AND last_used>=? ORDER BY last_used DESC,id DESC",
                (r,n)->new Device(r.getLong("id"),r.getString("device_name"),r.getObject("last_used",LocalDateTime.class),
                        r.getString("series").equals(currentSeries)),username,cutoff());
    }
    public void link(String series,String username,String sessionId,String userAgent) {
        int updated=db.update("UPDATE persistent_logins SET device_name=? WHERE series=? AND username=? AND last_used>=?",
                deviceName(userAgent),series,username,cutoff());
        if(updated!=0) db.update("INSERT INTO remembered_device_sessions(session_id,series) VALUES(?,?) ON CONFLICT(session_id) DO UPDATE SET series=EXCLUDED.series",sessionId,series);
    }
    public boolean valid(String username,String series) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM persistent_logins WHERE username=? AND series=? AND last_used>=?)",
                Boolean.class,username,series,cutoff()));
    }
    public void revoke(String username,long id) {
        var series=db.query("SELECT series FROM persistent_logins WHERE username=? AND id=?",(r,n)->r.getString(1),username,id);
        if(series.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        revokeSeries(username,series.getFirst());
    }
    public void revokeSeries(String username,String series) {
        db.query("SELECT s.session_id FROM remembered_device_sessions s JOIN persistent_logins p ON p.series=s.series WHERE p.username=? AND p.series=?",
                (r,n)->r.getString(1),username,series).forEach(this::expire);
        db.update("DELETE FROM persistent_logins WHERE username=? AND series=?",username,series);
    }
    public void revokeOthers(String username,String currentSeries,String currentSessionId) {
        for(Object principal:sessions.getAllPrincipals()) {
            String name=principal instanceof UserDetails details?details.getUsername():principal.toString();
            if(name.equals(username)) sessions.getAllSessions(principal,false).stream()
                    .filter(s->!s.getSessionId().equals(currentSessionId)).forEach(s->s.expireNow());
        }
        if(currentSeries==null) db.update("DELETE FROM persistent_logins WHERE username=?",username);
        else db.update("DELETE FROM persistent_logins WHERE username=? AND series<>?",username,currentSeries);
    }
    private void expire(String sessionId) {var session=sessions.getSessionInformation(sessionId);if(session!=null)session.expireNow();}
    @org.springframework.context.event.EventListener
    public void sessionDestroyed(org.springframework.security.core.session.SessionDestroyedEvent event) {
        db.update("DELETE FROM remembered_device_sessions WHERE session_id=?",event.getId());
    }
    private static java.sql.Timestamp cutoff() {return new java.sql.Timestamp(System.currentTimeMillis()-VALIDITY_SECONDS*1000L);}
    private static String deviceName(String agent) {
        if(agent==null) return "Browser";
        String platform=agent.contains("iPhone")?"iPhone":agent.contains("iPad")?"iPad":agent.contains("Android")?"Android":
                agent.contains("Windows")?"Windows":agent.contains("Macintosh")?"Mac":"Browser";
        String browser=agent.contains("Edg/")?"Edge":agent.contains("Firefox")||agent.contains("FxiOS")?"Firefox":
                agent.contains("Chrome")||agent.contains("CriOS")?"Chrome":agent.contains("Safari")?"Safari":"";
        return platform+(browser.isEmpty()?"":" / "+browser);
    }
    public record Device(long id,String name,LocalDateTime lastUsed,boolean current) {
        public LocalDateTime expiresAt() {return lastUsed.plusDays(90);}
    }
}
