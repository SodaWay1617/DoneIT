package com.doneit.mcp.security;

import java.security.Principal;

public record McpIdentity(String tokenId, String username, boolean write) implements Principal {
    public static final String ATTRIBUTE = McpIdentity.class.getName();
    @Override public String getName() { return username; }
}
