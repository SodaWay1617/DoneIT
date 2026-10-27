package com.doneit.mcp.security;

import java.time.Instant;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("doneit.mcp")
public record McpProperties(boolean enabled,
        @DefaultValue({"localhost", "127.0.0.1", "[::1]"}) List<String> allowedHosts,
        @DefaultValue List<String> allowedOrigins,
        @DefaultValue("65536") int maxRequestBytes,
        @DefaultValue("120") int requestsPerMinute,
        @DefaultValue List<Token> tokens) {
    public McpProperties {
        allowedHosts=allowedHosts==null?List.of("localhost","127.0.0.1","[::1]"):List.copyOf(allowedHosts);
        allowedOrigins=allowedOrigins==null?List.of():List.copyOf(allowedOrigins);
        tokens=tokens==null?List.of():List.copyOf(tokens);
    }
    public record Token(String id, String username, String sha256, boolean write, Instant expiresAt) {}
}
