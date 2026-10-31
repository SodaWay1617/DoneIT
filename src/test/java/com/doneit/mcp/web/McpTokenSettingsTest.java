package com.doneit.mcp.web;

import com.doneit.mcp.application.McpTokenService;
import com.doneit.mcp.infrastructure.McpTokenStore;
import com.doneit.mcp.security.McpProperties;
import com.doneit.mcp.security.McpTokenVerifier;
import com.doneit.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "doneit.mcp.enabled=true")
@AutoConfigureMockMvc
class McpTokenSettingsTest extends IntegrationTestSupport {
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired McpTokenService tokens;
    @Autowired McpTokenStore store;
    @Autowired McpTokenVerifier verifier;
    @Autowired McpProperties properties;
    @Autowired Clock clock;

    @BeforeEach void setup() {
        db.execute("TRUNCATE users CASCADE");
        db.update("INSERT INTO users(login,password_hash,created_at,updated_at) VALUES('owner','unused',NOW(),NOW()),('other','unused',NOW(),NOW())");
    }

    @Test void secureIssueStoresOnlyHashAndSecretNeverAppearsOnGet() throws Exception {
        var before = clock.instant();
        var response = mvc.perform(post("/settings/mcp-tokens").secure(true).with(user("owner")).with(csrf())
                        .param("name", "Laptop").param("allowWrite", "true"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
        var body = json.readTree(response.getContentAsString());
        var secret = body.path("secret").asText();
        assertThat(secret).matches("[A-Za-z0-9_-]{43}");
        assertThat(Duration.between(before, Instant.parse(body.path("expiresAt").asText())).toDays()).isEqualTo(21);
        assertThat(db.queryForObject("SELECT token_hash FROM mcp_tokens", String.class))
                .isEqualTo(HexFormat.of().formatHex(McpTokenVerifier.digest(secret))).doesNotContain(secret);
        assertThat(verifier.verify("Bearer " + secret)).hasValueSatisfying(identity -> {
            assertThat(identity.username()).isEqualTo("owner");
            assertThat(identity.write()).isTrue();
        });
        var page = mvc.perform(get("/settings/mcp-tokens").secure(true).with(user("owner")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Laptop", "name=\"_csrf\"", "mcp-tokens.js").doesNotContain(secret);
        assertThat(db.queryForObject("SELECT changes::text FROM mcp_audit WHERE tool='issue_token'", String.class)).doesNotContain(secret);
        mvc.perform(get("/settings/mcp-tokens?lang=ru").secure(true).with(user("owner")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("MCP-токены")));
    }

    @Test void issueRequiresAuthenticationHttpsCsrfAndValidLimits() throws Exception {
        mvc.perform(post("/settings/mcp-tokens").secure(true).with(csrf()).param("name","Laptop"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/settings/mcp-tokens").secure(true).with(user("owner")).param("name","Laptop"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/settings/mcp-tokens").with(user("owner")).with(csrf()).param("name","Laptop"))
                .andExpect(status().isForbidden());
        for (String days : new String[]{"0", "22"})
            mvc.perform(post("/settings/mcp-tokens").secure(true).with(user("owner")).with(csrf())
                            .param("name","Laptop").param("days",days)).andExpect(status().isBadRequest());
        mvc.perform(post("/settings/mcp-tokens").secure(true).with(user("owner")).with(csrf()).param("name"," "))
                .andExpect(status().isBadRequest());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM mcp_tokens", Integer.class)).isZero();
        for (int n=0; n<10; n++) tokens.issue("owner","Token " + n,1,false);
        mvc.perform(post("/settings/mcp-tokens").secure(true).with(user("owner")).with(csrf()).param("name","Excess"))
                .andExpect(status().isTooManyRequests());
    }

    @Test void ownershipExpiryAndRevocationAreEnforcedAndLegacyIsNotRevived() throws Exception {
        var issued = tokens.issue("owner","Private laptop",21,false);
        var token = tokens.list("owner").getFirst();
        assertThat(verifier.verify("Bearer " + issued.secret)).hasValueSatisfying(identity -> assertThat(identity.write()).isFalse());
        mvc.perform(get("/settings/mcp-tokens").secure(true).with(user("other")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Private laptop"))));
        mvc.perform(post("/settings/mcp-tokens/" + token.id() + "/revoke").secure(true).with(user("other")).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/settings/mcp-tokens/" + token.id() + "/revoke").secure(true).with(user("owner")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/settings/mcp-tokens/" + token.id() + "/revoke").secure(true).with(user("owner")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(verifier.verify("Bearer " + issued.secret)).isEmpty();
        var expiring = tokens.issue("owner","Expired",1,false);
        db.update("UPDATE mcp_tokens SET expires_at=NOW()-INTERVAL '1 second' WHERE name='Expired'");
        assertThat(verifier.verify("Bearer " + expiring.secret)).isEmpty();
        String secret = "legacy_test_token_0123456789012345678901234567890";
        var legacy = new McpProperties.Token("legacy", "owner", HexFormat.of().formatHex(McpTokenVerifier.digest(secret)), true, clock.instant().plusSeconds(3600));
        store.importLegacy(legacy);
        tokens.revoke("owner", "legacy");
        store.importLegacy(legacy);
        assertThat(verifier.verify("Bearer " + secret)).isEmpty();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM mcp_audit WHERE tool='revoke_token'", Integer.class)).isEqualTo(2);
    }
}
