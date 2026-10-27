package com.doneit.mcp.infrastructure;

import com.doneit.mcp.security.McpIdentity;
import com.doneit.project.application.ProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class McpAudit {
    private final JdbcTemplate db;
    private final ProjectService projects;
    private final ObjectMapper json;
    public McpAudit(JdbcTemplate db,ProjectService projects,ObjectMapper json) {this.db=db;this.projects=projects;this.json=json;}

    public void record(McpIdentity who,String tool,String request,Long task,String outcome,Object changes) {
        db.update("INSERT INTO mcp_audit(user_id,client_id,tool,request_id,task_id,outcome,changes) VALUES(?,?,?,?,?,?,?::jsonb)",
                projects.user().id(),who.tokenId(),tool,request,task,outcome,encode(changes));
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void failure(McpIdentity who,String tool,String outcome) {record(who,tool,null,null,outcome,Map.of());}
    private String encode(Object value) {
        try { return json.writeValueAsString(value); } catch (com.fasterxml.jackson.core.JsonProcessingException e) {throw new IllegalStateException("Cannot encode MCP audit");}
    }
}
