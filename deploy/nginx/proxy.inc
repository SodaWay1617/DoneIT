# Re-resolve the app address after Compose recreates its container.
resolver 127.0.0.11 valid=10s ipv6=off;
set $doneit_upstream http://app:8080;
proxy_http_version 1.1;
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $remote_addr;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-Host $host;
proxy_set_header X-Forwarded-Port $server_port;
proxy_set_header X-Forwarded-Prefix "";
proxy_set_header X-Forwarded-Ssl "";
# Discard client-supplied RFC 7239 headers before Spring processes them.
proxy_set_header Forwarded "";
proxy_set_header Connection "";
proxy_connect_timeout 10s;
proxy_read_timeout 3600s;
proxy_send_timeout 3600s;
# Streamable HTTP / MCP SSE responses must reach the client immediately.
proxy_buffering off;
proxy_cache off;
proxy_pass $doneit_upstream;
