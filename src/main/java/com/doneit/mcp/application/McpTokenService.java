package com.doneit.mcp.application;

import com.doneit.mcp.infrastructure.McpTokenStore;
import com.doneit.mcp.security.McpTokenVerifier;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class McpTokenService {
    private final McpTokenStore store;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public McpTokenService(McpTokenStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    // Deliberately no record/toString containing the secret.
    public static final class Issued {
        public final String secret;
        public final Instant expiresAt;
        Issued(String secret, Instant expiresAt) { this.secret = secret; this.expiresAt = expiresAt; }
    }

    public List<McpTokenStore.Token> list(String username) {
        return store.list(username, clock.instant());
    }

    @Transactional
    public Issued issue(String username, String name, int days, boolean write) {
        name = name == null ? "" : name.strip();
        if (name.isEmpty() || name.length() > 80 || name.chars().anyMatch(Character::isISOControl)
                || days < 1 || days > 21) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        long user = store.lockUser(username);
        var now = clock.instant();
        if (store.activeCount(user, now) >= 32 || store.recentCount(user, now.minus(Duration.ofHours(1))) >= 10)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var expiry = now.plus(Duration.ofDays(days));
        store.insert(user, "ui-" + UUID.randomUUID(), name,
                HexFormat.of().formatHex(McpTokenVerifier.digest(secret)), write, now, expiry);
        return new Issued(secret, expiry);
    }

    @Transactional
    public void revoke(String username, String id) {
        if (!store.revoke(username, id, clock.instant())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
