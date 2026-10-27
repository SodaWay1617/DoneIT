package com.doneit.mcp.application;

public class McpFailure extends RuntimeException {
    private final String code;
    public McpFailure(String code, String message) { super(message); this.code=code; }
    public String code() { return code; }
    public static McpFailure invalid(String message) { return new McpFailure("VALIDATION_ERROR",message); }
}
