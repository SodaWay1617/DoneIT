package com.doneit.mcp.infrastructure;

import com.doneit.mcp.security.McpIdentity;
import com.doneit.mcp.security.McpProperties;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@DependsOnDatabaseInitialization
public class McpTokenStore {
    private final JdbcTemplate db;

    public McpTokenStore(JdbcTemplate db) {
        this.db = db;
    }

    public record Token(String id, String name, boolean allowWrite, Instant createdAt,
                        Instant expiresAt, Instant revokedAt, boolean legacy, boolean active) {}

    // Import once, never overwrite a persisted expiry or revive a revoked token.
    public void importLegacy(McpProperties.Token token) {
        db.update("""
                INSERT INTO mcp_tokens(id,user_id,name,token_hash,allow_write,expires_at,legacy)
                SELECT ?,id,?,?,?, ?,TRUE FROM users WHERE login=?
                ON CONFLICT (id) DO NOTHING
                """, token.id(), token.id(), token.sha256().toLowerCase(), token.write(),
                Timestamp.from(token.expiresAt()), token.username());
    }

    public Optional<McpIdentity> verify(String hash, Instant now) {
        return db.query("""
                SELECT t.id,u.login,t.allow_write FROM mcp_tokens t JOIN users u ON u.id=t.user_id
                WHERE t.token_hash=? AND t.revoked_at IS NULL AND t.expires_at>?
                """, (rs, n) -> new McpIdentity(rs.getString(1), rs.getString(2), rs.getBoolean(3)),
                hash, Timestamp.from(now)).stream().findFirst();
    }

    public List<Token> list(String username, Instant now) {
        return db.query("""
                SELECT t.* FROM mcp_tokens t JOIN users u ON u.id=t.user_id WHERE u.login=?
                ORDER BY (t.revoked_at IS NULL AND t.expires_at>?) DESC,t.created_at DESC,t.id LIMIT 200
                """, (rs, n) -> {
                    var revoked = rs.getTimestamp("revoked_at");
                    var expiry = rs.getTimestamp("expires_at").toInstant();
                    return new Token(rs.getString("id"), rs.getString("name"), rs.getBoolean("allow_write"),
                            rs.getTimestamp("created_at").toInstant(), expiry,
                            revoked == null ? null : revoked.toInstant(), rs.getBoolean("legacy"),
                            revoked == null && expiry.isAfter(now));
                }, username, Timestamp.from(now));
    }

    public long lockUser(String username) {
        return db.queryForObject("SELECT id FROM users WHERE login=? FOR UPDATE", Long.class, username);
    }

    public int activeCount(long user, Instant now) {
        return db.queryForObject("SELECT COUNT(*) FROM mcp_tokens WHERE user_id=? AND revoked_at IS NULL AND expires_at>?",
                Integer.class, user, Timestamp.from(now));
    }

    public int recentCount(long user, Instant since) {
        return db.queryForObject("SELECT COUNT(*) FROM mcp_tokens WHERE user_id=? AND created_at>?",
                Integer.class, user, Timestamp.from(since));
    }

    public void insert(long user, String id, String name, String hash, boolean write, Instant now, Instant expiry) {
        db.update("""
                INSERT INTO mcp_tokens(id,user_id,name,token_hash,allow_write,created_at,expires_at)
                VALUES(?,?,?,?,?,?,?)
                """, id, user, name, hash, write, Timestamp.from(now), Timestamp.from(expiry));
        audit(user, id, "issue_token");
    }

    public boolean revoke(String username, String id, Instant now) {
        var owners = db.query("""
                UPDATE mcp_tokens SET revoked_at=? WHERE id=? AND revoked_at IS NULL
                AND user_id=(SELECT id FROM users WHERE login=?) RETURNING user_id
                """, (rs, n) -> rs.getLong(1), Timestamp.from(now), id, username);
        owners.forEach(owner -> audit(owner, id, "revoke_token"));
        return !owners.isEmpty();
    }

    private void audit(long user, String id, String action) {
        db.update("INSERT INTO mcp_audit(user_id,client_id,tool,outcome) VALUES(?,?,?,'OK')", user, id, action);
    }
}
