package com.doneit.mcp.security;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class McpTokenVerifierTest {
    static final String SECRET="test_only_token_not_a_real_secret_01234567890123456789";
    static final Instant NOW=Instant.parse("2026-10-27T12:00:00Z");
    static McpProperties properties(boolean enabled,Instant expiry,boolean write) {
        return new McpProperties(enabled,List.of("localhost"),List.of(),65536,120,
                List.of(new McpProperties.Token("laptop","owner",HexFormat.of().formatHex(McpTokenVerifier.digest(SECRET)),write,expiry)));
    }
    @Test void verifiesOnlyUnexpiredBearerTokensAndKeepsClientAndPermissions() {
        var verifier=new McpTokenVerifier(properties(true,NOW.plusSeconds(3600),false),Clock.fixed(NOW,ZoneOffset.UTC));
        assertThat(verifier.verify("Bearer "+SECRET)).contains(new McpIdentity("laptop","owner",false));
        assertThat(verifier.verify("Bearer "+SECRET+"wrong")).isEmpty();
        assertThat(verifier.verify("Basic "+SECRET)).isEmpty();
        assertThat(verifier.verify(null)).isEmpty();
        assertThat(new McpTokenVerifier(properties(true,NOW,false),Clock.fixed(NOW,ZoneOffset.UTC)).verify("Bearer "+SECRET)).isEmpty();
        assertThat(new McpTokenVerifier(properties(false,NOW.plusSeconds(3600),true),Clock.fixed(NOW,ZoneOffset.UTC)).verify("Bearer "+SECRET)).isEmpty();
    }
    @Test void enabledServerFailsClosedForMissingOrAmbiguousConfiguration() {
        var missing=new McpProperties(true,List.of("localhost"),List.of(),65536,120,List.of());
        assertThatThrownBy(()->new McpTokenVerifier(missing,Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
        var token=properties(true,NOW,true).tokens().getFirst();
        var duplicate=new McpProperties(true,List.of("localhost"),List.of(),65536,120,List.of(token,token));
        assertThatThrownBy(()->new McpTokenVerifier(duplicate,Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
    }
}
