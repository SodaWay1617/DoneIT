package com.doneit.mcp.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Optional;

public class McpTokenVerifier {
    private final McpProperties properties;
    private final Clock clock;
    private final com.doneit.mcp.infrastructure.McpTokenStore store;

    public McpTokenVerifier(McpProperties properties, Clock clock) {
        this(properties, clock, null);
    }

    public McpTokenVerifier(McpProperties properties, Clock clock, com.doneit.mcp.infrastructure.McpTokenStore store) {
        this.properties = properties;
        this.clock = clock;
        this.store = store;
        if (!properties.enabled()) return;
        if ((store == null && properties.tokens().isEmpty()) || properties.tokens().size() > 32
                || properties.allowedHosts().isEmpty() || properties.maxRequestBytes() < 1024
                || properties.maxRequestBytes() > 1_048_576 || properties.requestsPerMinute() < 1
                || properties.requestsPerMinute() > 10000) throw new IllegalStateException("Invalid MCP configuration");
        var ids = new HashSet<String>();
        var hashes = new HashSet<String>();
        for (var token : properties.tokens()) {
            if (token.id() == null || !token.id().matches("[A-Za-z0-9._-]{1,64}") || !ids.add(token.id())
                    || token.username() == null || token.username().isBlank() || token.expiresAt() == null
                    || token.sha256() == null || !token.sha256().matches("[a-fA-F0-9]{64}")
                    || !hashes.add(token.sha256().toLowerCase())) throw new IllegalStateException("Invalid MCP token configuration");
        }
        if (store != null) properties.tokens().forEach(store::importLegacy);
    }

    public Optional<McpIdentity> verify(String header) {
        if (!properties.enabled() || header == null || !header.startsWith("Bearer ")) return Optional.empty();
        String raw = header.substring(7);
        if (!raw.matches("[A-Za-z0-9_-]{43,256}")) return Optional.empty();
        byte[] digest = digest(raw);
        if (store != null) return store.verify(HexFormat.of().formatHex(digest), clock.instant());
        McpIdentity identity = null;
        for (var token : properties.tokens()) {
            boolean matches = MessageDigest.isEqual(digest, HexFormat.of().parseHex(token.sha256()));
            if (matches && token.expiresAt().isAfter(clock.instant())) identity = new McpIdentity(token.id(), token.username(), token.write());
        }
        return Optional.ofNullable(identity);
    }

    public static byte[] digest(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
