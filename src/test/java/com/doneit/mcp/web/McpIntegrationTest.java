package com.doneit.mcp.web;

import com.doneit.mcp.security.McpTokenVerifier;
import com.doneit.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.*;
import io.modelcontextprotocol.client.*;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"doneit.mcp.enabled=true","doneit.mcp.requests-per-minute=1000"})
class McpIntegrationTest extends IntegrationTestSupport {
    static final String OWNER="test_only_owner_token_012345678901234567890123456789";
    static final String OTHER="test_only_other_token_012345678901234567890123456789";
    static final String READ="test_only_read_token_0123456789012345678901234567890";
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;

    @DynamicPropertySource static void tokens(DynamicPropertyRegistry registry) {
        String[] secrets={OWNER,OTHER,READ};
        for(int i=0;i<secrets.length;i++) {
            String prefix="doneit.mcp.tokens["+i+"].",secret=secrets[i];int index=i;
            registry.add(prefix+"id",()->"client"+index);
            registry.add(prefix+"username",()->index==1?"other":"owner");
            registry.add(prefix+"sha256",()->HexFormat.of().formatHex(McpTokenVerifier.digest(secret)));
            registry.add(prefix+"write",()->index!=2);
            registry.add(prefix+"expires-at",()->"2099-01-01T00:00:00Z");
        }
    }
    @BeforeEach void setup() {
        db.execute("TRUNCATE users CASCADE");
        db.update("INSERT INTO users(login,password_hash,created_at,updated_at) VALUES('owner','unused',NOW(),NOW()),('other','unused',NOW(),NOW())");
    }
    McpSyncClient client(String token) {
        var transport=HttpClientStreamableHttpTransport.builder("http://localhost:"+port)
                .customizeRequest(b->b.header("Authorization","Bearer "+token)).openConnectionOnStartup(false).build();
        var client=McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).initializationTimeout(Duration.ofSeconds(10)).build();
        client.initialize();return client;
    }
    JsonNode call(McpSyncClient client,String tool,Map<String,Object> args) throws Exception {
        var response=client.callTool(new McpSchema.CallToolRequest(tool,args));
        var content=(McpSchema.TextContent)response.content().getFirst();
        var result=json.readTree(content.text());
        if(Boolean.TRUE.equals(response.isError())) return result;
        assertThat(result.has("code")).isFalse();return result;
    }
    Map<String,Object> creation(String key,String title) {return Map.of("requestId",key,"task",Map.of("title",title));}

    @Test void realProtocolExposesOnlySixToolsAndCrudPreservesProtectedFields() throws Exception {
        try(var client=client(OWNER)) {
            assertThat(client.listTools().tools()).extracting(McpSchema.Tool::name)
                    .containsExactlyInAnyOrder("list_projects","list_tasks","get_task","create_task","update_task","close_task");
            var created=call(client,"create_task",creation("crud","Buy tickets"));
            long id=created.path("task").path("id").asLong();
            assertThat(created.path("task").path("status").asText()).isEqualTo("NEW");
            assertThat(created.path("task").path("plannedForAt").isNull()).isTrue();
            db.update("UPDATE tasks SET spent_minutes=45 WHERE id=?",id);
            Long owner=db.queryForObject("SELECT id FROM users WHERE login='owner'",Long.class);
            Long tag=db.queryForObject("INSERT INTO tags(user_id,name,name_key) VALUES(?,'Prime','prime') RETURNING id",Long.class,owner);
            db.update("INSERT INTO task_tags(tag_id,task_id) VALUES(?,?)",tag,id);
            var before=call(client,"get_task",Map.of("taskId",id)).path("task");
            var updated=call(client,"update_task",Map.of("taskId",id,"expectedRevision",before.path("revision").asLong(),"changes",Map.of("title","Renamed"))).path("task");
            assertThat(updated.path("title").asText()).isEqualTo("Renamed");
            assertThat(updated.path("spentMinutes").asInt()).isEqualTo(45);
            assertThat(updated.path("tags").get(0).path("name").asText()).isEqualTo("Prime");
            assertThat(updated.path("revision").asLong()).isGreaterThan(before.path("revision").asLong());
            var stale=call(client,"update_task",Map.of("taskId",id,"expectedRevision",before.path("revision").asLong(),"changes",Map.of("title","Overwrite")));
            assertThat(stale.path("code").asText()).isEqualTo("CONFLICT");
            var closed=call(client,"close_task",Map.of("taskId",id,"expectedRevision",updated.path("revision").asLong()));
            assertThat(closed.path("task").path("status").asText()).isEqualTo("CLOSED");
            assertThat(call(client,"list_tasks",Map.of()).path("tasks").isEmpty()).isTrue();
            assertThat(call(client,"list_tasks",Map.of("statuses",List.of("CLOSED"))).path("tasks").size()).isEqualTo(1);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM mcp_audit WHERE outcome='OK'",Integer.class)).isGreaterThanOrEqualTo(6);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM mcp_audit WHERE outcome='CONFLICT'",Integer.class)).isEqualTo(1);
        }
    }

    @Test void duplicateCreationIsAtomicAcrossConcurrentClientsAndRejectsDifferentPayloads() throws Exception {
        try(var first=client(OWNER);var second=client(OWNER);var pool=Executors.newFixedThreadPool(2)) {
            var args=creation("same-request","Only once");
            var one=pool.submit(()->call(first,"create_task",args));var two=pool.submit(()->call(second,"create_task",args));
            assertThat(one.get(15,TimeUnit.SECONDS).path("task").path("id").asLong()).isEqualTo(two.get(15,TimeUnit.SECONDS).path("task").path("id").asLong());
            assertThat(db.queryForObject("SELECT COUNT(*) FROM tasks",Integer.class)).isEqualTo(1);
            assertThat(call(first,"create_task",creation("same-request","Different")).path("code").asText()).isEqualTo("CONFLICT");
        }
    }

    @Test void concurrentUpdatesCannotSilentlyOverwriteAndUsersStayIsolatedOnSdkThreads() throws Exception {
        try(var owner=client(OWNER);var other=client(OTHER);var second=client(OWNER);var pool=Executors.newFixedThreadPool(2)) {
            var one=pool.submit(()->call(owner,"create_task",creation("parallel","Owner task")));
            var two=pool.submit(()->call(other,"create_task",creation("parallel","Other task")));
            var a=one.get(15,TimeUnit.SECONDS).path("task");var b=two.get(15,TimeUnit.SECONDS).path("task");
            assertThat(a.path("projectId").asLong()).isNotEqualTo(b.path("projectId").asLong());
            assertThat(call(other,"get_task",Map.of("taskId",a.path("id").asLong())).path("code").asText()).isEqualTo("NOT_FOUND");
            var update=Map.<String,Object>of("taskId",a.path("id").asLong(),"expectedRevision",a.path("revision").asLong(),"changes",Map.of("description","First"));
            var update2=Map.<String,Object>of("taskId",a.path("id").asLong(),"expectedRevision",a.path("revision").asLong(),"changes",Map.of("description","Second"));
            var firstUpdate=pool.submit(()->call(owner,"update_task",update));var secondUpdate=pool.submit(()->call(second,"update_task",update2));
            var outcomes=List.of(firstUpdate.get(15,TimeUnit.SECONDS),secondUpdate.get(15,TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter(n->"CONFLICT".equals(n.path("code").asText())).count()).isEqualTo(1);
        }
    }

    @Test void readOnlyAndValidationErrorsDoNotChangeStateOrLeaveIdempotencyReservations() throws Exception {
        try(var read=client(READ);var owner=client(OWNER)) {
            assertThat(call(read,"list_projects",Map.of()).path("projects").size()).isEqualTo(1);
            assertThat(call(read,"create_task",creation("denied","Must not exist")).path("code").asText()).isEqualTo("FORBIDDEN");
            assertThat(call(owner,"create_task",Map.of("requestId","bad-project","task",Map.of("title","Hidden","projectId",999999))).path("code").asText()).isEqualTo("NOT_FOUND");
            assertThat(db.queryForObject("SELECT COUNT(*) FROM tasks",Integer.class)).isZero();
            assertThat(db.queryForObject("SELECT COUNT(*) FROM mcp_requests",Integer.class)).isZero();
            assertThat(db.queryForObject("SELECT COUNT(*) FROM mcp_audit WHERE outcome IN ('NOT_FOUND','FORBIDDEN')",Integer.class)).isEqualTo(2);
        }
    }

    @Test void httpAuthenticationIsIndependentFromCookiesAndSessionsAreBoundToClients() throws Exception {
        var http=HttpClient.newHttpClient();String url="http://localhost:"+port+"/mcp";
        var absent=http.send(HttpRequest.newBuilder(URI.create(url)).header("Cookie","JSESSIONID=web-cookie").GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(absent.statusCode()).isEqualTo(401);
        var blocked=http.send(HttpRequest.newBuilder(URI.create(url)).header("Authorization","Bearer "+OWNER).header("Origin","https://attacker.example").GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(blocked.statusCode()).isEqualTo(403);
        String initialize="{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}";
        var initialized=http.send(HttpRequest.newBuilder(URI.create(url)).header("Authorization","Bearer "+OWNER)
                .header("Content-Type","application/json").header("Accept","application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(initialize)).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(initialized.statusCode()).isEqualTo(200);
        String session=initialized.headers().firstValue("Mcp-Session-Id").orElseThrow();
        var stolen=http.send(HttpRequest.newBuilder(URI.create(url)).header("Authorization","Bearer "+OTHER).header("Mcp-Session-Id",session)
                .header("Accept","text/event-stream").GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(stolen.statusCode()).isEqualTo(404);
    }
}
